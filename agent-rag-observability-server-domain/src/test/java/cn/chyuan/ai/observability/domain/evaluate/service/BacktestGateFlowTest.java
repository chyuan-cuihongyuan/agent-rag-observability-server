package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IAnswerSourceProvider;
import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IEvalMetricsPort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmJudgePort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateRecordEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.AnswerSample;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.JudgeVerdict;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RetrievalMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 回测端到端测试（工单 0136 R4 验收）— mock 执行链全真服务串联：
 * 任务创建（BacktestService→EvaluateService）→ 异步执行（runTask→EvalExecutionService，
 * 单测直调等价同步）→ 门禁判定（GateJudgeService→GateDecisionEngine）→ 记录可查（GateService）。
 * <p>
 * 挂接方式裁定说明：不引入轮询/消息队列，门禁判定在任务完成回写处回调——
 * 执行链只加一处回调即闭环，CI/CD 两跳（查任务状态 → 查门禁记录）拿结论。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("回测门控闭环端到端测试")
class BacktestGateFlowTest {

    @Mock
    private IEvalTaskRepository evalTaskRepository;
    @Mock
    private IEvalResultRepository evalResultRepository;
    @Mock
    private IEvalDatasetRepository evalDatasetRepository;
    @Mock
    private IGateRepository gateRepository;
    @Mock
    private IGateRecordRepository gateRecordRepository;
    @Mock
    private RetrievalMetricCalculator metricCalculator;
    @Mock
    private IAnswerSourceProvider answerSourceProvider;
    @Mock
    private ILlmJudgePort llmJudgePort;
    @Mock
    private IEvalMetricsPort evalMetricsPort;
    @Mock
    private RubricService rubricService;

    private void wireAndRun(GateEntity gate, String itemsJson, JudgeVerdict verdict) {
        // 1. 真实服务串联：EvaluateService（@Resource 字段注入执行引擎）→ 执行 → 判定 → 记录
        EvaluateService evaluateService = new EvaluateService(
                evalTaskRepository, evalResultRepository, evalDatasetRepository);
        EvalExecutionService executionService = new EvalExecutionService(
                evalTaskRepository, evalResultRepository, evalDatasetRepository,
                metricCalculator, answerSourceProvider, llmJudgePort, evalMetricsPort,
                rubricService, new GateJudgeService(gateRepository, gateRecordRepository, new GateDecisionEngine()));
        ReflectionTestUtils.setField(evaluateService, "evalExecutionService", executionService);
        BacktestService backtestService = new BacktestService(gateRepository, evalDatasetRepository, evaluateService);

        // 2. 仓储 stub：任务保存后可按 taskId 回查（runTask 取任务）；判定记录落账后可按 taskId 查
        AtomicReference<EvalTaskEntity> savedTask = new AtomicReference<>();
        doAnswer(inv -> {
            savedTask.set(inv.getArgument(0));
            return null;
        }).when(evalTaskRepository).save(any(EvalTaskEntity.class));
        when(evalTaskRepository.queryByTaskId(anyString())).thenAnswer(inv -> savedTask.get());
        when(gateRepository.queryByGateId("gate-1")).thenReturn(gate);
        when(evalDatasetRepository.queryByPool("golden", 1, 1)).thenReturn(List.of(
                EvalDatasetEntity.builder().datasetId("ds-1").datasetName("黄金集").version(2)
                        .pool("golden").itemsJson(itemsJson).build()));
        when(evalDatasetRepository.queryByDatasetId("ds-1")).thenReturn(EvalDatasetEntity.builder()
                .datasetId("ds-1").datasetName("黄金集").version(2).pool("golden").itemsJson(itemsJson).build());
        when(rubricService.resolveWeights("ANSWER_QUALITY"))
                .thenReturn(BuiltinRubrics.defaultWeights("ANSWER_QUALITY"));
        when(answerSourceProvider.fetch(anyString(), isNull())).thenReturn(AnswerSample.builder()
                .traceId("t-1").actualAnswer("ans").retrievedChunks(List.of("c1")).build());
        when(metricCalculator.compute(anyList(), anyList(), anyString(), anyString()))
                .thenReturn(RetrievalMetrics.builder().recall(1.0).precision(1.0).f1(1.0)
                        .top3HitRate(1.0).mrr(1.0).ndcg(1.0).map(1.0).answerSimilarity(0.5).build());
        when(llmJudgePort.judge(anyString(), anyString(), anyString(), anyList())).thenReturn(verdict);

        // 3. 触发回测（pool 模式）：创建任务 → runTask 直调同步执行 → 完成回调门禁判定
        Map<String, String> result = backtestService.triggerBacktest(null, "golden", "gate-1",
                null, "model-v2", null);
        assertNotNull(result.get("taskId"));

        // 4. 记录可查：门禁记录按任务查询（CI 第二跳）
        when(gateRecordRepository.queryByTaskId(result.get("taskId"))).thenAnswer(inv -> insertedRecord.get());
        GateService gateService = new GateService(gateRepository, gateRecordRepository);
        GateRecordEntity record = gateService.queryRecordByTaskId(result.get("taskId"));
        assertNotNull(record, "回测完成后门禁记录应可查");
        assertEquals(result.get("taskId"), record.getTaskId());
        assertEquals(expectedResult, record.getResult(), "门禁结论");
    }

    /** 落账记录捕获（judgeAndRecord 内部 insert 的实体） */
    private final AtomicReference<GateRecordEntity> insertedRecord = new AtomicReference<>();
    /** 期望结论（由具体场景在 wire 前设定） */
    private String expectedResult;

    @Test
    @DisplayName("端到端 PASS — 全达标：任务 COMPLETED + 安全/分数全过 → 记录 PASS 可查")
    public void testEndToEndPass() {
        expectedResult = "PASS";
        doAnswer(inv -> {
            insertedRecord.set(inv.getArgument(0));
            return null;
        }).when(gateRecordRepository).insert(any(GateRecordEntity.class));

        // 门禁：safety {hallucination>=0.3} + score {overall>=0.6, passRate>=1.0}；trials=2
        GateEntity gate = GateEntity.builder().gateId("gate-1").name("发布门禁").trials(2).enabled(true)
                .safetyDims(Map.of("hallucination", 0.3))
                .scoreThresholds(Map.of("overall", 0.6, "passRate", 1.0))
                .build();
        // 高分评判：overall ≈ 0.9175、幻觉率 0.05 → 安全分 0.95
        JudgeVerdict verdict = JudgeVerdict.builder()
                .faithfulness(0.95).relevance(0.9).hallucinationRate(0.05).completeness(0.9)
                .similarity(0.9).answerCorrectness(0.9).contextPrecision(0.9).contextRecall(0.9)
                .contextRelevance(0.9).detail("ok").degraded(false).unknownKeys(List.of()).build();

        wireAndRun(gate, "[{\"query\":\"q1\",\"standardAnswer\":\"a1\"}]", verdict);

        // 任务侧：COMPLETED + Pass@k 汇总回写（trials 取门禁配置 2）
        verify(evalTaskRepository).updateStatus(anyString(), eq("COMPLETED"));
        verify(evalTaskRepository).updatePassStatistics(anyString(), eq(1.0), eq(0.0));
        verify(evalTaskRepository).updateTotalCount(anyString(), eq(2));
    }

    @Test
    @DisplayName("端到端 BLOCK — 安全一票否决：总分达标但幻觉越限 → 记录 BLOCK 可查（含触发明细）")
    public void testEndToEndSafetyVeto() {
        expectedResult = "BLOCK";
        doAnswer(inv -> {
            insertedRecord.set(inv.getArgument(0));
            return null;
        }).when(gateRecordRepository).insert(any(GateRecordEntity.class));

        // 门禁：safety {hallucination>=0.9}（幻觉率上限 0.1）——评判幻觉率 0.4 → 安全分 0.6 越限
        GateEntity gate = GateEntity.builder().gateId("gate-1").name("发布门禁").trials(1).enabled(true)
                .safetyDims(Map.of("hallucination", 0.9))
                .scoreThresholds(Map.of("overall", 0.6))
                .build();
        JudgeVerdict verdict = JudgeVerdict.builder()
                .faithfulness(0.9).relevance(0.9).hallucinationRate(0.4).completeness(0.9)
                .similarity(0.9).answerCorrectness(0.9).contextPrecision(0.9).contextRecall(0.9)
                .contextRelevance(0.9).detail("ok").degraded(false).unknownKeys(List.of()).build();

        wireAndRun(gate, "[{\"query\":\"q1\",\"standardAnswer\":\"a1\"}]", verdict);

        assertTrue(insertedRecord.get().getTriggerDetail().contains("hallucination"), "触发明细应含安全维度");
    }
}
