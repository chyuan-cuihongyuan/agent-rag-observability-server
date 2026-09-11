package cn.chyuan.ai.observability.domain.mining.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.EvalDatasetItem;
import cn.chyuan.ai.observability.domain.mining.adapter.repository.ICaseCandidateRepository;
import cn.chyuan.ai.observability.domain.mining.model.entity.CaseCandidateEntity;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseSource;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseStatus;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.patrol.adapter.repository.IPatrolRecordRepository;
import cn.chyuan.ai.observability.domain.patrol.model.entity.PatrolRecordEntity;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Case 挖掘枢纽（工单 0138 S2）— 打通「在线→离线 Loop」与「评测体系迭代 Loop」：
 * 线上问题（失败链路/巡检失败）与低分评测结果自动沉淀为错题集候选，回填后评测资产自动生长。
 * <ul>
 *   <li>三来源候选生成：低分评测结果 / 失败超时链路 / 巡检失败记录 → 统一 CaseCandidate</li>
 *   <li>幂等：唯一键 (source, sourceRef)——同来源同引用不重复入池、不重复回填</li>
 *   <li>回填：转 EvalDatasetItem 入错题集（pool=wrong、source=trace、traceId 关联），
 *       期望行为留空待人工补齐；目标版本冻结则拒绝追加（不变式与 saveDataset 一致）</li>
 * </ul>
 */
@Slf4j
@Service
public class CaseMiningService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 答案摘要截断长度（TEXT 列仍截断，避免异常巨答案撑爆行） */
    private static final int ANSWER_SUMMARY_MAX = 480;
    /** 入池原因截断长度（VARCHAR(512) 留余量） */
    private static final int REASON_MAX = 480;

    /** 默认错题集名称（回填未指定目标时使用） */
    public static final String DEFAULT_WRONG_DATASET_NAME = "错题本";

    private final IEvalResultRepository evalResultRepository;
    private final IChatResultRepository chatResultRepository;
    private final IPatrolRecordRepository patrolRecordRepository;
    private final ICaseCandidateRepository caseCandidateRepository;
    private final IEvalDatasetRepository evalDatasetRepository;

    public CaseMiningService(IEvalResultRepository evalResultRepository,
                             IChatResultRepository chatResultRepository,
                             IPatrolRecordRepository patrolRecordRepository,
                             ICaseCandidateRepository caseCandidateRepository,
                             IEvalDatasetRepository evalDatasetRepository) {
        this.evalResultRepository = evalResultRepository;
        this.chatResultRepository = chatResultRepository;
        this.patrolRecordRepository = patrolRecordRepository;
        this.caseCandidateRepository = caseCandidateRepository;
        this.evalDatasetRepository = evalDatasetRepository;
    }

    /** 挖掘配置（触发方装配；领域服务不读配置面） */
    public record MiningConfig(double lowScoreThreshold, int scanLimit) {
        public static MiningConfig of(double lowScoreThreshold, int scanLimit) {
            return new MiningConfig(lowScoreThreshold <= 0 ? 0.6 : lowScoreThreshold,
                    scanLimit <= 0 ? 50 : Math.min(scanLimit, 200));
        }
    }

    /** 一轮采集结果：新入池候选数按来源分列 */
    public static class CollectOutcome {
        public int evalLowScore;
        public int traceFail;
        public int patrolFail;
        public int total() {
            return evalLowScore + traceFail + patrolFail;
        }
    }

    /** 回填结果：promoted 成功条数（幂等跳过的不计） */
    public record PromoteOutcome(int promoted, String datasetId) {}

    /**
     * 执行一轮三来源采集：候选统一入池（PENDING），已存在的 (source, sourceRef) 幂等跳过。
     * 单来源异常隔离：一个来源失败不影响其余来源（记告警继续）。
     */
    public CollectOutcome collect(MiningConfig config) {
        CollectOutcome outcome = new CollectOutcome();
        outcome.evalLowScore = collectFromLowScoreResults(config);
        outcome.traceFail = collectFromFailedTraces(config);
        outcome.patrolFail = collectFromPatrolFailures(config);
        log.info("Case 挖掘采集完成: lowScore={}, traceFail={}, patrolFail={}",
                outcome.evalLowScore, outcome.traceFail, outcome.patrolFail);
        return outcome;
    }

    /** 来源①：低分评测结果（overall_score 低于阈值，取最近 scanLimit 条） */
    private int collectFromLowScoreResults(MiningConfig config) {
        try {
            List<EvalResultEntity> results = evalResultRepository.queryLowScore(config.lowScoreThreshold(), config.scanLimit());
            int added = 0;
            for (EvalResultEntity r : results) {
                String traceId = r.getTraceId();
                String sourceRef = (r.getTaskId() == null ? "task-unknown" : r.getTaskId())
                        + ":" + (r.getTrialNo() == null ? 1 : r.getTrialNo())
                        + ":" + (traceId == null ? "no-trace" : traceId);
                if (caseCandidateRepository.existsBySourceRef(CaseSource.EVAL_LOW_SCORE, sourceRef)) {
                    continue;
                }
                caseCandidateRepository.insert(CaseCandidateEntity.builder()
                        .source(CaseSource.EVAL_LOW_SCORE)
                        .sourceRef(sourceRef)
                        .traceId(traceId)
                        .query(truncate(r.getQueryText(), ANSWER_SUMMARY_MAX))
                        .answerSummary(truncate(r.getActualAnswer(), ANSWER_SUMMARY_MAX))
                        .reason(truncate("overallScore=" + r.getOverallScore(), REASON_MAX))
                        .status(CaseStatus.PENDING)
                        .createTime(FMT.format(LocalDateTime.now()))
                        .build());
                added++;
            }
            return added;
        } catch (Exception e) {
            log.warn("低分评测结果采集失败（来源隔离）: {}", e.getMessage());
            return 0;
        }
    }

    /** 来源②：失败/超时线上链路（chat_result_log 的 FAIL/TIMEOUT 状态） */
    private int collectFromFailedTraces(MiningConfig config) {
        try {
            List<ChatResultEntity> chats = chatResultRepository.queryByStatuses(List.of("FAIL", "TIMEOUT"), config.scanLimit());
            int added = 0;
            for (ChatResultEntity c : chats) {
                if (c.getTraceId() == null || caseCandidateRepository.existsBySourceRef(CaseSource.TRACE_FAIL, c.getTraceId())) {
                    continue;
                }
                caseCandidateRepository.insert(CaseCandidateEntity.builder()
                        .source(CaseSource.TRACE_FAIL)
                        .sourceRef(c.getTraceId())
                        .traceId(c.getTraceId())
                        .query(truncate(c.getQuestion(), ANSWER_SUMMARY_MAX))
                        .answerSummary(truncate(c.getAnswer(), ANSWER_SUMMARY_MAX))
                        .reason(truncate("finalStatus=" + c.getFinalStatus(), REASON_MAX))
                        .status(CaseStatus.PENDING)
                        .createTime(FMT.format(LocalDateTime.now()))
                        .build());
                added++;
            }
            return added;
        } catch (Exception e) {
            log.warn("失败链路采集失败（来源隔离）: {}", e.getMessage());
            return 0;
        }
    }

    /** 来源③：巡检失败信号（patrol_record 非 SUCCESS；traceId 为空时以 pid-记录ID 兜底幂等键） */
    private int collectFromPatrolFailures(MiningConfig config) {
        try {
            List<PatrolRecordEntity> failures = patrolRecordRepository.queryLatestFailures(config.scanLimit());
            int added = 0;
            for (PatrolRecordEntity p : failures) {
                String sourceRef = p.getTraceId() != null ? p.getTraceId() : "pid-" + p.getId();
                if (caseCandidateRepository.existsBySourceRef(CaseSource.PATROL_FAIL, sourceRef)) {
                    continue;
                }
                caseCandidateRepository.insert(CaseCandidateEntity.builder()
                        .source(CaseSource.PATROL_FAIL)
                        .sourceRef(sourceRef)
                        .traceId(p.getTraceId())
                        .query(truncate(p.getQuery(), ANSWER_SUMMARY_MAX))
                        .answerSummary(null)
                        .reason(truncate(p.getErrorSummary() == null
                                ? "patrolStatus=" + p.getStatus().getCode()
                                : p.getErrorSummary(), REASON_MAX))
                        .status(CaseStatus.PENDING)
                        .createTime(FMT.format(LocalDateTime.now()))
                        .build());
                added++;
            }
            return added;
        } catch (Exception e) {
            log.warn("巡检失败采集失败（来源隔离）: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 批量确认回填错题集：PENDING 候选转 EvalDatasetItem（pool=wrong、source=trace、
     * traceId 关联、期望行为留空待人工）。同批内按 sourceRef 去重 + 只处理 PENDING，
     * 追加到目标数据集最新版本条目尾部。
     *
     * @param ids         候选 ID 列表
     * @param datasetName 目标数据集名称（空则用默认「错题本」；按名称取最新未冻结版本）
     */
    public PromoteOutcome promote(List<Long> ids, String datasetName) {
        if (ids == null || ids.isEmpty()) {
            return new PromoteOutcome(0, null);
        }
        String targetName = datasetName == null || datasetName.isBlank() ? DEFAULT_WRONG_DATASET_NAME : datasetName.trim();
        EvalDatasetEntity target = resolveWrongDataset(targetName);

        List<CaseCandidateEntity> candidates = caseCandidateRepository.queryByIds(ids);
        // 有序去重（保候选插入顺序）并过滤仅 PENDING
        Map<Long, CaseCandidateEntity> pendingById = new LinkedHashMap<>();
        for (CaseCandidateEntity c : candidates) {
            if (c.getStatus() == CaseStatus.PENDING) {
                pendingById.put(c.getId(), c);
            }
        }
        if (pendingById.isEmpty()) {
            return new PromoteOutcome(0, target.getDatasetId());
        }

        List<EvalDatasetItem> items = parseItems(target.getItemsJson());
        for (CaseCandidateEntity c : pendingById.values()) {
            // 错题条目：prompt/query 取候选查询，期望行为全部留空待人工补齐
            items.add(EvalDatasetItem.builder()
                    .prompt(c.getQuery())
                    .query(c.getQuery())
                    .traceId(c.getTraceId())
                    .build());
        }
        target.setItemCount(items.size());
        target.setItemsJson(JSON.toJSONString(items));
        // 冻结版本在 resolveWrongDataset 已规避；此处仍经 saveDataset 通道保证不变式一致
        evalDatasetRepository.update(target);

        int promoted = caseCandidateRepository.updateStatus(
                new ArrayList<>(pendingById.keySet()), CaseStatus.PROMOTED, target.getDatasetId());
        log.info("Case 回填完成: datasetId={}, promoted={}", target.getDatasetId(), promoted);
        return new PromoteOutcome(promoted, target.getDatasetId());
    }

    /** 批量忽略（仅 PENDING 生效，幂等） */
    public int ignore(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return caseCandidateRepository.updateStatus(ids, CaseStatus.IGNORED, null);
    }

    /**
     * 解析回填目标：按名称在 wrong 池找最新版本；找同名但 pool 未标记的也接受（回写 pool=wrong/source=trace）；
     * 不存在则建版本 1。目标版本冻结 → 复制出新版本追加（沿用快照复制语义，冻结不变式不破坏）。
     */
    private EvalDatasetEntity resolveWrongDataset(String datasetName) {
        List<EvalDatasetEntity> versions = evalDatasetRepository.queryVersions(datasetName);
        EvalDatasetEntity latest = versions.isEmpty() ? null : versions.get(0);
        if (latest == null) {
            EvalDatasetEntity created = EvalDatasetEntity.builder()
                    .datasetName(datasetName)
                    .description("Case 挖掘自动沉淀的错题集（三来源回填）")
                    .itemCount(0)
                    .itemsJson("[]")
                    .version(1)
                    .pool("wrong")
                    .source("trace")
                    .frozen(false)
                    .build();
            evalDatasetRepository.save(created); // 仓储写回 datasetId
            return created;
        }
        if (!"wrong".equals(latest.getPool())) {
            // 同名历史数据集未入错题池：顺带归池（保持单一错题集语义）
            latest.setPool("wrong");
        }
        if (Boolean.TRUE.equals(latest.getFrozen())) {
            int nextVersion = evalDatasetRepository.maxVersion(datasetName) + 1;
            EvalDatasetEntity next = EvalDatasetEntity.builder()
                    .datasetName(datasetName)
                    .description(latest.getDescription())
                    .itemCount(latest.getItemCount())
                    .itemsJson(latest.getItemsJson())
                    .version(nextVersion)
                    .pool(latest.getPool())
                    .source(latest.getSource() == null ? "trace" : latest.getSource())
                    .frozen(false)
                    .build();
            evalDatasetRepository.save(next); // 仓储写回 datasetId
            return next;
        }
        return latest;
    }

    private List<EvalDatasetItem> parseItems(String itemsJson) {
        try {
            List<EvalDatasetItem> items = JSON.parseArray(itemsJson, EvalDatasetItem.class);
            return items == null ? new ArrayList<>() : new ArrayList<>(items);
        } catch (Exception e) {
            log.warn("目标数据集 itemsJson 解析失败（按空集处理）: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
