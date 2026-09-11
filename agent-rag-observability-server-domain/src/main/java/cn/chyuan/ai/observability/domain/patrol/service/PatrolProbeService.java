package cn.chyuan.ai.observability.domain.patrol.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IAnswerSourceProvider;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.AnswerSample;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.EvalDatasetItem;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.domain.observe.service.TraceQualityCalculator;
import cn.chyuan.ai.observability.domain.patrol.adapter.port.IPatrolMetricsPort;
import cn.chyuan.ai.observability.domain.patrol.adapter.repository.IPatrolRecordRepository;
import cn.chyuan.ai.observability.domain.patrol.model.entity.PatrolRecordEntity;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundConfig;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundSummary;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 巡检拨测领域服务（工单 0137 S1）— 在线评测三手段之「巡检」的执行内核：
 * <ul>
 *   <li>种子解析：固定查询集优先，否则从 golden 池数据集条目抽取 query</li>
 *   <li>拨测执行：复用在线回放 provider 既有链路，completion future 超时口径
 *       （单次拨测独立超时预算，TIMEOUT 后不再等待底层调用）</li>
 *   <li>轻量评分：复用 TraceQualityCalculator 启发式（不调 LLM，控制巡检成本），
 *       取非空维度均值；无可评估维度时分数置 null</li>
 *   <li>隔离性：单个种子失败/异常不阻塞同轮后续种子，更不阻塞下一轮调度</li>
 * </ul>
 */
@Slf4j
@Service
public class PatrolProbeService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 错误摘要入库截断长度（VARCHAR(512) 留余量） */
    private static final int ERROR_SUMMARY_MAX = 480;
    /** 种子 taskRef 截断长度（VARCHAR(128) 留余量） */
    private static final int TASK_REF_MAX = 120;

    private final IAnswerSourceProvider answerSourceProvider;
    private final TraceQualityCalculator traceQualityCalculator;
    private final IPatrolRecordRepository patrolRecordRepository;
    private final IPatrolMetricsPort patrolMetricsPort;
    private final IEvalDatasetRepository evalDatasetRepository;

    /** 拨测专用有界线程池：超时放弃后底层调用可继续在后台完成并自行回收，不占用调度线程 */
    private final ExecutorService probeExecutor;

    public PatrolProbeService(IAnswerSourceProvider answerSourceProvider,
                              TraceQualityCalculator traceQualityCalculator,
                              IPatrolRecordRepository patrolRecordRepository,
                              IPatrolMetricsPort patrolMetricsPort,
                              IEvalDatasetRepository evalDatasetRepository) {
        this.answerSourceProvider = answerSourceProvider;
        this.traceQualityCalculator = traceQualityCalculator;
        this.patrolRecordRepository = patrolRecordRepository;
        this.patrolMetricsPort = patrolMetricsPort;
        this.evalDatasetRepository = evalDatasetRepository;
        this.probeExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "patrol-probe");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 执行一轮巡检拨测。
     *
     * @param config 轮次配置（超时预算/目标 agent/种子来源）
     * @return 本轮汇总；无可用种子时返回 total=0 的空汇总（记录告警，不视为失败）
     */
    public PatrolRoundSummary runRound(PatrolRoundConfig config) {
        String roundId = "P" + System.currentTimeMillis();
        List<PatrolSeed> seeds = resolveSeeds(config);
        if (seeds.isEmpty()) {
            log.warn("巡检轮次 {} 无可用种子（queries 与 datasetId 均未命中），本轮跳过", roundId);
            return PatrolRoundSummary.builder()
                    .roundId(roundId).total(0).success(0).fail(0).timeout(0)
                    .avgScore(null).finishedAt(FMT.format(LocalDateTime.now()))
                    .build();
        }

        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicInteger timeout = new AtomicInteger();
        List<Double> scores = new ArrayList<>();

        for (PatrolSeed seed : seeds) {
            // 单种子隔离：任何异常都收敛为一条 FAIL 记录，不中断本轮后续种子
            try {
                PatrolRecordEntity record = probeOne(roundId, seed, config);
                patrolRecordRepository.insert(record);
                patrolMetricsPort.recordProbeFinished(record.getStatus(), record.getDurationMs(), record.getScore());
                switch (record.getStatus()) {
                    case SUCCESS -> {
                        success.incrementAndGet();
                        if (record.getScore() != null) {
                            scores.add(record.getScore());
                        }
                    }
                    case TIMEOUT -> timeout.incrementAndGet();
                    default -> fail.incrementAndGet();
                }
            } catch (Exception e) {
                log.warn("巡检种子执行异常（隔离为 FAIL）, roundId={}, query={}", roundId, seed.query, e);
                fail.incrementAndGet();
                insertFailRecord(roundId, seed, config, e.getMessage());
            }
        }

        PatrolRoundSummary summary = PatrolRoundSummary.builder()
                .roundId(roundId)
                .total(seeds.size())
                .success(success.get())
                .fail(fail.get())
                .timeout(timeout.get())
                .avgScore(scores.isEmpty() ? null : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0))
                .finishedAt(FMT.format(LocalDateTime.now()))
                .build();
        log.info("巡检轮次完成: roundId={}, total={}, success={}, fail={}, timeout={}, avgScore={}",
                roundId, summary.getTotal(), summary.getSuccess(), summary.getFail(), summary.getTimeout(), summary.getAvgScore());
        return summary;
    }

    /** 拨测单个种子：future 超时 → TIMEOUT；provider 返回空/异常 → FAIL；成功 → SUCCESS + 轻量分 */
    private PatrolRecordEntity probeOne(String roundId, PatrolSeed seed, PatrolRoundConfig config) {
        long timeoutMs = config.getTimeoutMs() > 0 ? config.getTimeoutMs() : 30_000L;
        long start = System.currentTimeMillis();

        CompletableFuture<AnswerSample> future = CompletableFuture.supplyAsync(
                () -> answerSourceProvider.fetch(seed.query, effectiveAgentId(config)), probeExecutor);
        AnswerSample sample;
        try {
            sample = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return buildRecord(roundId, seed, config, PatrolStatus.TIMEOUT, null,
                    timeoutMs, "拨测超时（预算 " + timeoutMs + "ms）", null);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            return buildRecord(roundId, seed, config, PatrolStatus.FAIL, null,
                    cost, rootMessage(e), null);
        }

        long cost = System.currentTimeMillis() - start;
        if (sample == null) {
            return buildRecord(roundId, seed, config, PatrolStatus.FAIL, null,
                    cost, "provider 返回空结果（上游不可用或响应不合法）", null);
        }
        Double score = lightweightScore(seed.query, sample);
        return buildRecord(roundId, seed, config, PatrolStatus.SUCCESS, score, cost, null, sample.getTraceId());
    }

    /**
     * 轻量质量分：把 AnswerSample 适配为 TraceQualityCalculator 的入参形态
     * （合成 chat/retrieval 实体），取非空维度均值。纯启发式，不调 LLM。
     */
    private Double lightweightScore(String query, AnswerSample sample) {
        try {
            ChatResultEntity chat = ChatResultEntity.builder()
                    .question(query)
                    .answer(sample.getActualAnswer())
                    .finalStatus("SUCCESS")
                    .build();
            List<String> chunks = sample.getRetrievedChunks() == null ? List.of() : sample.getRetrievedChunks();
            RagRetrievalEntity retrieval = RagRetrievalEntity.builder()
                    .sourceDocs(chunks.isEmpty() ? null : JSON.toJSONString(chunks))
                    .retrievalCount(chunks.size())
                    .emptyRetrieval(chunks.isEmpty() ? 1 : 0)
                    .build();
            TraceQualityCalculator.TraceQuality quality = traceQualityCalculator.compute(retrieval, chat);
            // 三维均值只统计可评估维度（null 表示该维度无信号，不参与均值）
            List<Double> dims = new ArrayList<>(3);
            if (quality.retrievalQuality() != null) dims.add(quality.retrievalQuality());
            if (quality.faithfulness() != null) dims.add(quality.faithfulness());
            if (quality.answerRelevance() != null) dims.add(quality.answerRelevance());
            return dims.isEmpty() ? null : dims.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        } catch (Exception e) {
            // 评分失败不影响拨测结论本身
            log.debug("巡检轻量评分失败（分数置空）: {}", e.getMessage());
            return null;
        }
    }

    /** 种子解析：固定查询集优先；否则按 datasetId 拉 golden 池数据集条目 */
    private List<PatrolSeed> resolveSeeds(PatrolRoundConfig config) {
        List<PatrolSeed> seeds = new ArrayList<>();
        if (config.getQueries() != null && !config.getQueries().isEmpty()) {
            AtomicInteger seq = new AtomicInteger(1);
            for (String q : config.getQueries()) {
                if (q != null && !q.isBlank()) {
                    seeds.add(new PatrolSeed("q-" + seq.getAndIncrement(), q.trim()));
                }
            }
            return seeds;
        }
        if (config.getDatasetId() == null || config.getDatasetId().isBlank()) {
            return seeds;
        }
        EvalDatasetEntity dataset = evalDatasetRepository.queryByDatasetId(config.getDatasetId());
        if (dataset == null || dataset.getItemsJson() == null || dataset.getItemsJson().isBlank()) {
            log.warn("巡检种子数据集不存在或无条目: datasetId={}", config.getDatasetId());
            return seeds;
        }
        try {
            List<EvalDatasetItem> items = JSON.parseArray(dataset.getItemsJson(), EvalDatasetItem.class);
            AtomicInteger seq = new AtomicInteger(1);
            for (EvalDatasetItem item : items == null ? List.<EvalDatasetItem>of() : items) {
                // 优先三元组 prompt 字段（对齐线上真实输入），缺省回退 query
                String q = item != null ? (firstNonBlank(item.getPrompt(), item.getQuery())) : null;
                if (q != null && !q.isBlank()) {
                    seeds.add(new PatrolSeed("ds-" + seq.getAndIncrement(), q.trim()));
                }
            }
        } catch (Exception e) {
            log.warn("巡检种子数据集 itemsJson 解析失败: datasetId={}", config.getDatasetId(), e);
        }
        return seeds;
    }

    private String effectiveAgentId(PatrolRoundConfig config) {
        return config.getAgentId() != null && !config.getAgentId().isBlank() ? config.getAgentId() : null;
    }

    private PatrolRecordEntity buildRecord(String roundId, PatrolSeed seed, PatrolRoundConfig config,
                                           PatrolStatus status, Double score, long durationMs,
                                           String errorSummary, String traceId) {
        return PatrolRecordEntity.builder()
                .roundId(roundId)
                .taskRef(truncate(seed.taskRef, TASK_REF_MAX))
                .query(seed.query)
                .agentId(config.getAgentId())
                .status(status)
                .score(score)
                .durationMs(durationMs)
                .errorSummary(truncate(errorSummary, ERROR_SUMMARY_MAX))
                .traceId(traceId)
                .createTime(FMT.format(LocalDateTime.now()))
                .build();
    }

    private void insertFailRecord(String roundId, PatrolSeed seed, PatrolRoundConfig config, String message) {
        try {
            patrolRecordRepository.insert(buildRecord(roundId, seed, config, PatrolStatus.FAIL, null,
                    0, rootMessage(new RuntimeException(message)), null));
        } catch (Exception e2) {
            log.warn("巡检 FAIL 记录落库失败, roundId={}", roundId, e2);
        }
    }

    private String rootMessage(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    /** 拨测种子（内部值对象） */
    private record PatrolSeed(String taskRef, String query) {}
}
