package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IAnswerSourceProvider;
import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IEvalMetricsPort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmJudgePort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.AnswerSample;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.JudgeVerdict;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RetrievalMetrics;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.TaskEvalSummary;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pass@k 多试验执行链测试（工单 0135 R3 验收）：
 * <ul>
 *   <li>k=1 完全回归：结果行 trialNo 恒 1、total=样本数、无门禁回调（与旧行为一致）</li>
 *   <li>k=3 多试验：结果行 = 样本数×k、trialNo 覆盖 1..k、答案源独立走 k 次</li>
 *   <li>进度口径：total/completed 按总 trial 比例（样本数×k）</li>
 *   <li>Pass@k 汇总回写：passRate（至少 1 次达标）+ scoreStdDev（per-trial 均值标准差）</li>
 *   <li>R4 挂接：绑定 gate 的任务完成/失败后回调判定（summary/失败口径）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Pass@k 多试验执行链测试")
class EvalExecutionServiceTrialTest {

    @Mock
    private IEvalTaskRepository evalTaskRepository;
    @Mock
    private IEvalResultRepository evalResultRepository;
    @Mock
    private IEvalDatasetRepository evalDatasetRepository;
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
    @Mock
    private GateJudgeService gateJudgeService;

    @InjectMocks
    private EvalExecutionService service;

    // ANSWER_QUALITY 口径：overall = 0.25*faith + 0.68（其余维度固定 0.9/幻觉 0.05）
    private static final double FAITH_HIGH = 0.9;
    private static final double FAITH_LOW = 0.2;

    private void stubCommon(int sampleCount) {
        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < sampleCount; i++) {
            if (i > 0) items.append(",");
            items.append("{\"query\":\"q").append(i).append("\",\"standardAnswer\":\"a").append(i).append("\"}");
        }
        items.append("]");
        EvalDatasetEntity dataset = EvalDatasetEntity.builder()
                .datasetId("ds-1").itemsJson(items.toString()).build();
        when(evalDatasetRepository.queryByDatasetId("ds-1")).thenReturn(dataset);
        when(rubricService.resolveWeights("ANSWER_QUALITY"))
                .thenReturn(BuiltinRubrics.defaultWeights("ANSWER_QUALITY"));
        when(answerSourceProvider.fetch(anyString(), isNull()))
                .thenReturn(AnswerSample.builder().traceId("t-1").actualAnswer("ans")
                        .retrievedChunks(List.of("c1")).build());
        when(metricCalculator.compute(anyList(), anyList(), anyString(), anyString()))
                .thenReturn(RetrievalMetrics.builder()
                        .recall(1.0).precision(1.0).f1(1.0).top3HitRate(1.0)
                        .mrr(1.0).ndcg(1.0).map(1.0).answerSimilarity(0.5).build());
    }

    /** 固定高/低 faithfulness 的评判（其余维度 0.9，幻觉率 0.05） */
    private JudgeVerdict verdict(double faith) {
        return JudgeVerdict.builder()
                .faithfulness(faith).relevance(0.9).hallucinationRate(0.05)
                .completeness(0.9).similarity(0.9).answerCorrectness(0.9)
                .contextPrecision(0.9).contextRecall(0.9).contextRelevance(0.9)
                .detail("ok").degraded(false).unknownKeys(List.of()).build();
    }

    @Test
    @DisplayName("k=1 回归 — trialNo 恒 1、total=样本数、passRate 退化为单次口径、无门禁回调")
    public void testK1Regression() {
        stubCommon(2);
        when(llmJudgePort.judge(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(verdict(FAITH_HIGH));
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId("task-k1").evalType("ANSWER_QUALITY").datasetId("ds-1")
                .trials(null).passThreshold(null) // 缺省：k=1、阈值 0.5
                .build();

        service.execute(task);

        // 进度口径不变：total = 样本数（×1）
        verify(evalTaskRepository).updateTotalCount("task-k1", 2);
        // 结果行：每样本一行，trialNo 恒 1
        ArgumentCaptor<List<EvalResultEntity>> captor = ArgumentCaptor.captor();
        verify(evalResultRepository).batchSave(captor.capture());
        List<EvalResultEntity> results = captor.getValue();
        assertEquals(2, results.size(), "k=1 结果行数 = 样本数");
        assertTrue(results.stream().allMatch(r -> r.getTrialNo() == 1), "k=1 trialNo 恒 1");
        // eval_detail 携带 trialNo=1（明细口径不变 + 新字段）
        JSONObject detail = JSON.parseObject(results.get(0).getEvalDetail());
        assertEquals(1, detail.getIntValue("trialNo"));
        // 状态与汇总回写
        verify(evalTaskRepository).updateStatus("task-k1", "COMPLETED");
        verify(evalTaskRepository).updatePassStatistics(eq("task-k1"), eq(1.0), eq(0.0));
        // 无 gate 绑定 → 无门禁回调
        verify(gateJudgeService, never()).judgeAndRecord(any(), any());
        // 答案源只走 1 遍
        verify(answerSourceProvider, times(2)).fetch(anyString(), isNull());
    }

    @Test
    @DisplayName("k=3 多试验 — 结果行 = 样本数×k、trialNo 覆盖 1..k、答案源独立走 k 遍")
    public void testK3Trials() {
        stubCommon(2);
        when(llmJudgePort.judge(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(verdict(FAITH_HIGH));
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId("task-k3").evalType("ANSWER_QUALITY").datasetId("ds-1")
                .trials(3).build();

        service.execute(task);

        verify(evalTaskRepository).updateTotalCount("task-k3", 6);
        ArgumentCaptor<List<EvalResultEntity>> captor = ArgumentCaptor.captor();
        verify(evalResultRepository, atLeastOnce()).batchSave(captor.capture());
        List<EvalResultEntity> results = captor.getAllValues().stream()
                .flatMap(List::stream).toList();
        assertEquals(6, results.size(), "结果行数 = 样本数 × k");
        // trial 维度：1..3 各 2 行（每 trial 覆盖全部样本）
        for (int t = 1; t <= 3; t++) {
            int trial = t;
            assertEquals(2, results.stream().filter(r -> r.getTrialNo() == trial).count(),
                    "trial " + t + " 应覆盖全部样本");
        }
        // 每样本独立走 3 次答案源（共 6 次）
        verify(answerSourceProvider, times(6)).fetch(anyString(), isNull());
        // 进度完成数按总 trial 比例累计到 6
        verify(evalTaskRepository, atLeastOnce()).updateProgress(eq("task-k3"), eq(6), anyDouble());
    }

    @Test
    @DisplayName("Pass@k 汇总 — 至少 1 次达标即通过；方差 = per-trial 均值标准差（手算对照）")
    public void testPassAtKAggregation() {
        stubCommon(1);
        // 3 次 trial 的 faithfulness 依次低/高/低：overall = 0.73 / 0.905 / 0.73
        AtomicInteger call = new AtomicInteger();
        when(llmJudgePort.judge(anyString(), anyString(), anyString(), anyList()))
                .thenAnswer(inv -> verdict(call.getAndIncrement() == 1 ? FAITH_HIGH : FAITH_LOW));
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId("task-pk").evalType("ANSWER_QUALITY").datasetId("ds-1")
                .trials(3).passThreshold(0.85).build(); // 阈值可配：0.85 时仅 trial2 达标

        service.execute(task);

        // passRate = 1.0（k 次中至少 1 次达标），stdDev = round(sqrt(((0.73-μ)²·2 + (0.905-μ)²)/3)) = 0.0825
        verify(evalTaskRepository).updatePassStatistics(eq("task-pk"), eq(1.0), eq(0.0825));
        // 均值口径不变：全部行算术平均 = round((0.73+0.905+0.73)/3)（落库 4 位小数口径）
        double avg = Math.round(((0.73 + 0.905 + 0.73) / 3) * 10000.0) / 10000.0;
        verify(evalTaskRepository, atLeastOnce()).updateProgress(eq("task-pk"), eq(3), eq(avg));
    }

    @Test
    @DisplayName("k 次全部不达标 — passRate=0（Pass@k 不放宽口径）")
    public void testPassAtKAllFail() {
        stubCommon(1);
        when(llmJudgePort.judge(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(verdict(FAITH_LOW));
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId("task-f").evalType("ANSWER_QUALITY").datasetId("ds-1")
                .trials(2).passThreshold(0.9).build(); // 0.73 < 0.9 两次都不达标

        service.execute(task);

        verify(evalTaskRepository).updatePassStatistics(eq("task-f"), eq(0.0), eq(0.0));
    }

    @Test
    @DisplayName("R4 挂接 — 绑定 gate 的任务完成后回调判定（汇总含 Pass@k 与维度均值）")
    public void testGateCallbackOnCompleted() {
        stubCommon(1);
        when(llmJudgePort.judge(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(verdict(FAITH_HIGH));
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId("task-g").evalType("ANSWER_QUALITY").datasetId("ds-1")
                .trials(2).gateId("gate-1").build();

        service.execute(task);

        ArgumentCaptor<TaskEvalSummary> captor = ArgumentCaptor.forClass(TaskEvalSummary.class);
        verify(gateJudgeService).judgeAndRecord(eq(task), captor.capture());
        TaskEvalSummary summary = captor.getValue();
        assertEquals(2, summary.getTrials());
        assertEquals(1, summary.getSampleCount());
        assertEquals(1.0, summary.getPassRate(), 1e-9);
        // 维度均值（正向安全分口径）：hallucination = 1 - 0.05 = 0.95
        assertEquals(0.95, summary.getDimensionAvg().get("hallucination"), 1e-9);
        assertNotNull(summary.getAvgOverall());
    }

    @Test
    @DisplayName("R4 挂接 — 执行异常置 FAILED 并回调判定（summary=null → 门禁按 BLOCK）")
    public void testGateCallbackOnFailed() {
        when(evalDatasetRepository.queryByDatasetId("ds-1"))
                .thenThrow(new RuntimeException("db down"));
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId("task-x").evalType("ANSWER_QUALITY").datasetId("ds-1")
                .gateId("gate-1").build();

        service.execute(task);

        verify(evalTaskRepository).updateStatus("task-x", "FAILED");
        verify(gateJudgeService).judgeAndRecord(task, null);
    }

    @Test
    @DisplayName("R4 挂接 — 空数据集 FAILED 也回调判定（防静默放行）")
    public void testGateCallbackOnEmptyDataset() {
        when(evalDatasetRepository.queryByDatasetId("ds-empty"))
                .thenReturn(EvalDatasetEntity.builder().datasetId("ds-empty").itemsJson("[]").build());
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId("task-e").evalType("ANSWER_QUALITY").datasetId("ds-empty")
                .gateId("gate-1").build();

        service.execute(task);

        verify(evalTaskRepository).updateStatus("task-e", "FAILED");
        verify(gateJudgeService).judgeAndRecord(task, null);
    }

    @Test
    @DisplayName("进度按总 trial 比例 — 2 样本 × 5 trial：total=10、completed 累计至 10")
    public void testProgressByTotalTrials() {
        stubCommon(2);
        AtomicInteger call = new AtomicInteger();
        when(llmJudgePort.judge(anyString(), anyString(), anyString(), anyList()))
                .thenAnswer(inv -> verdict(call.getAndIncrement() % 2 == 0 ? FAITH_HIGH : FAITH_LOW));
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId("task-p").evalType("ANSWER_QUALITY").datasetId("ds-1")
                .trials(5).build();

        service.execute(task);

        verify(evalTaskRepository).updateTotalCount("task-p", 10);
        verify(evalTaskRepository, atLeastOnce()).updateProgress(eq("task-p"), eq(10), anyDouble());
        verify(evalTaskRepository).updateStatus("task-p", "COMPLETED");
        // 全部样本在 5 次 trial 中至少一次拿到高 faithfulness → passRate=1
        verify(evalTaskRepository).updatePassStatistics(eq("task-p"), eq(1.0), anyDouble());
    }

    @Test
    @DisplayName("trials 非法兜底 — null/<1 按 1 执行（防御式，服务层已兜底）")
    public void testTrialsFallback() {
        stubCommon(1);
        when(llmJudgePort.judge(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(verdict(FAITH_HIGH));
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId("task-b").evalType("ANSWER_QUALITY").datasetId("ds-1")
                .trials(0).build();

        service.execute(task);

        // trials=0 与 null 等价 k=1：total=样本数、结果 1 行、答案源走 1 遍
        verify(evalTaskRepository).updateTotalCount("task-b", 1);
        verify(answerSourceProvider, times(1)).fetch(anyString(), isNull());
        ArgumentCaptor<List<EvalResultEntity>> captor = ArgumentCaptor.captor();
        verify(evalResultRepository).batchSave(captor.capture());
        assertEquals(1, captor.getValue().size());
    }
}
