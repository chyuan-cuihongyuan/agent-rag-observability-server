package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 回测服务（工单 0136 R4）— 变更门控闭环的触发入口：
 * 入参 datasetId（或 pool）+ gateId → 创建评测任务（复用 runTask 异步执行链，
 * trials/passThreshold 取门禁与默认配置，gateId 绑定到任务）→ 立即返回 taskId。
 * <p>
 * 同步等待任务完成不可行（runTask 为 @Async）：裁定「创建任务后立即返回 taskId，
 * 门禁判定在任务完成回写处挂接」（见 GateJudgeService），CI/CD 轮询
 * GET /task/{taskId} 到 COMPLETED/FAILED 后查 GET /gate/record/task/{taskId} 拿结论。
 * <ul>
 *   <li>datasetId 模式：直查指定数据集版本</li>
 *   <li>pool 模式：取该池最新一条数据集（queryByPool 首页首条，create_time 降序口径）</li>
 *   <li>evalType 默认 ANSWER_QUALITY（数据集不携带类型锚点，调用方可显式指定）</li>
 * </ul>
 */
@Slf4j
@Service
public class BacktestService {

    /** pool 模式取池内数据集的取数口径：最新一条 */
    private static final int POOL_PICK_PAGE = 1;
    private static final int POOL_PICK_SIZE = 1;

    private final IGateRepository gateRepository;
    private final IEvalDatasetRepository evalDatasetRepository;
    private final EvaluateService evaluateService;

    public BacktestService(IGateRepository gateRepository,
                           IEvalDatasetRepository evalDatasetRepository,
                           EvaluateService evaluateService) {
        this.gateRepository = gateRepository;
        this.evalDatasetRepository = evalDatasetRepository;
        this.evaluateService = evaluateService;
    }

    /**
     * 触发回测：创建任务并异步执行，立即返回 taskId（门禁结论在任务完成后落 eval_gate_record）。
     *
     * @param datasetId 数据集业务 ID（与 pool 二选一，优先 datasetId）
     * @param pool      样本池（golden/challenge/wrong；datasetId 为空时取该池最新一条）
     * @param gateId    门禁规则 ID（须存在且启用）
     * @param evalType  评测类型（缺省 ANSWER_QUALITY）
     */
    public Map<String, String> triggerBacktest(String datasetId, String pool, String gateId, String evalType,
                                               String modelVersion, String ragStrategyVersion) {
        // 1. 门禁校验：存在 + 启用
        GateEntity gate = gateRepository.queryByGateId(gateId);
        if (gate == null) {
            throw new IllegalArgumentException("Gate 不存在: " + gateId);
        }
        if (Boolean.FALSE.equals(gate.getEnabled())) {
            throw new IllegalArgumentException("Gate 已停用，无法触发回测: " + gateId);
        }

        // 2. 数据集解析：datasetId 直查；否则按池取最新一条
        EvalDatasetEntity dataset = resolveDataset(datasetId, pool);

        // 3. 创建任务：trials 取门禁配置，gateId 绑定（完成后回调门禁判定）
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskName("回测-" + gate.getName())
                .evalType(evalType == null || evalType.isBlank() ? "ANSWER_QUALITY" : evalType)
                .datasetId(dataset.getDatasetId())
                .modelVersion(modelVersion)
                .ragStrategyVersion(ragStrategyVersion)
                .trials(gate.getTrials() == null ? 1 : gate.getTrials())
                .gateId(gate.getGateId())
                .build();
        String taskId = evaluateService.createTask(task);
        // 4. 异步执行（@Async observeExecutor；立即返回）
        evaluateService.runTask(taskId);
        log.info("回测任务已触发, taskId={}, datasetId={}, gateId={}, trials={}",
                taskId, dataset.getDatasetId(), gate.getGateId(), task.getTrials());
        return Map.of("taskId", taskId, "gateId", gate.getGateId(), "datasetId", dataset.getDatasetId());
    }

    private EvalDatasetEntity resolveDataset(String datasetId, String pool) {
        if (datasetId != null && !datasetId.isBlank()) {
            EvalDatasetEntity dataset = evalDatasetRepository.queryByDatasetId(datasetId);
            if (dataset == null) {
                throw new IllegalArgumentException("数据集不存在: " + datasetId);
            }
            return dataset;
        }
        if (pool != null && !pool.isBlank()) {
            List<EvalDatasetEntity> list = evalDatasetRepository.queryByPool(pool, POOL_PICK_PAGE, POOL_PICK_SIZE);
            if (list == null || list.isEmpty()) {
                throw new IllegalArgumentException("样本池内无数据集: " + pool);
            }
            return list.get(0);
        }
        throw new IllegalArgumentException("datasetId 与 pool 必须提供其一");
    }
}
