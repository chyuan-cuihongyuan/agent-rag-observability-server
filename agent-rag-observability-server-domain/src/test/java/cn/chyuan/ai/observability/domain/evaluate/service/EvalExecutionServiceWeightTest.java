package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IAnswerSourceProvider;
import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IEvalMetricsPort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmJudgePort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.types.enums.EvalType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 综合分权重口径对照测试（工单 0133 R1 验收）：
 * computeOverall 权重改由 Rubric 读取后，同一输入的综合分与旧硬编码 v2 switch 同口径
 * （内置 Rubric 种子权重 = v2 权重）；EvalType 枚举五类型全覆盖无 switch 缺口。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("综合分 Rubric 权重对照测试")
public class EvalExecutionServiceWeightTest {

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

    // 一组确定的输入指标（构造非平凡值避免 0/1 边界掩盖权重差异）
    private static final double F1 = 0.62, TOP3 = 0.81, MRR = 0.55, NDCG = 0.73;
    private static final double FAITH = 0.86, REL = 0.74, COMP = 0.65, SIM = 0.58, CORRECT = 0.79;
    private static final double HALLUC = 0.12;
    private static final double CTXP = 0.67, CTXR = 0.71, CTXREL = 0.52;
    private static final double TOOL_SEL = 0.9, TOOL_PARAM = 0.6;
    private static final double INTENT = 1.0, BRANCH = 0.0, REASONING = 0.5;

    private Map<String, Double> fullScores() {
        Map<String, Double> s = new HashMap<>();
        s.put("f1", F1);
        s.put("top3HitRate", TOP3);
        s.put("mrr", MRR);
        s.put("ndcg", NDCG);
        s.put("faithfulness", FAITH);
        s.put("relevancy", REL);
        s.put("completeness", COMP);
        s.put("similarity", SIM);
        s.put("answerCorrectness", CORRECT);
        s.put("hallucination", 1 - HALLUC); // 反向维度
        s.put("contextPrecision", CTXP);
        s.put("contextRecall", CTXR);
        s.put("contextRelevance", CTXREL);
        s.put("toolSelection", TOOL_SEL);
        s.put("toolParam", TOOL_PARAM);
        s.put("intent", INTENT);
        s.put("branch", BRANCH);
        s.put("reasoning", REASONING);
        return s;
    }

    private void stubWeights(String evalType) {
        when(rubricService.resolveWeights(evalType)).thenReturn(BuiltinRubrics.defaultWeights(evalType));
    }

    /** 旧 v2 硬编码公式（迁移前的 switch 逐字口径，作为对照基准） */
    private static double legacyOverall(String evalType) {
        switch (evalType) {
            case "RAG_RETRIEVAL":
                return F1 * 0.4 + TOP3 * 0.2 + MRR * 0.2 + NDCG * 0.2;
            case "ANSWER_QUALITY":
                return clamp(FAITH * 0.25 + REL * 0.25 + COMP * 0.15 + SIM * 0.05 + CORRECT * 0.2 + (1 - HALLUC) * 0.1);
            case "CONTEXT_QUALITY":
                return clamp(CTXP * 0.4 + CTXR * 0.4 + CTXREL * 0.2);
            case "TOOL_CALL":
                return TOOL_SEL * 0.5 + TOOL_PARAM * 0.5;
            case "AGENT_DECISION":
                return INTENT * 0.3 + BRANCH * 0.3 + REASONING * 0.4;
            default:
                double retrievalScore = F1 * 0.4 + TOP3 * 0.2 + MRR * 0.2 + NDCG * 0.2;
                double contextScore = clamp(CTXP * 0.4 + CTXR * 0.4 + CTXREL * 0.2);
                double qualityScore = clamp(FAITH * 0.35 + REL * 0.35 + (1 - HALLUC) * 0.3);
                return retrievalScore * 0.4 + contextScore * 0.2 + qualityScore * 0.4;
        }
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    @Test
    @DisplayName("EvalType 枚举五类型全覆盖 — 每种类型的 Rubric 权重综合分与旧 v2 口径一致")
    public void testAllFiveEvalTypesMatchLegacy() {
        assertEquals(5, EvalType.values().length, "EvalType 应补齐五类型（含 CONTEXT_QUALITY/TOOL_CALL）");
        for (EvalType type : EvalType.values()) {
            stubWeights(type.getCode());
            double actual = service.computeOverall(
                    rubricService.resolveWeights(type.getCode()), fullScores());
            double expected = legacyOverall(type.getCode());
            assertEquals(expected, actual, 1e-9,
                    type.getCode() + " 的 Rubric 权重综合分应与旧 v2 口径一致");
        }
    }

    @Test
    @DisplayName("未知 evalType — 默认口径（检索0.4+上下文0.2+质量0.4 平铺展开）与旧公式一致")
    public void testUnknownEvalTypeDefaultWeights() {
        double actual = service.computeOverall(BuiltinRubrics.defaultWeights("UNKNOWN_TYPE"), fullScores());
        assertEquals(legacyOverall("UNKNOWN_TYPE"), actual, 1e-9, "默认口径平铺展开应与旧层级公式严格等价");
    }

    @Test
    @DisplayName("自定义 Rubric 权重生效 — 权重表驱动覆盖内置口径")
    public void testCustomRubricWeights() {
        // 用户自建 Rubric：两维各 0.5
        Map<String, Double> custom = Map.of("faithfulness", 0.5, "relevancy", 0.5);
        double actual = service.computeOverall(custom, fullScores());
        assertEquals(FAITH * 0.5 + REL * 0.5, actual, 1e-9, "自定义权重应直接驱动综合分");
    }

    @Test
    @DisplayName("权重表缺失的指标 key 按 0 分计（不抛异常）")
    public void testMissingMetricKeyScoresZero() {
        Map<String, Double> weights = Map.of("notExistMetric", 1.0);
        assertEquals(0.0, service.computeOverall(weights, fullScores()), "未知 key 计 0 分");
    }

    @Test
    @DisplayName("综合分上限 clamp — 超界分数不越过 1")
    public void testClampUpperBound() {
        Map<String, Double> weights = Map.of("f1", 1.0);
        Map<String, Double> scores = Map.of("f1", 5.0);
        assertEquals(1.0, service.computeOverall(weights, scores), "综合分应 clamp 到 [0,1]");
    }

    @Test
    @DisplayName("内置权重表 — 五类型权重和均为 1（Rubric 校验口径自洽）")
    public void testBuiltinWeightSums() {
        for (EvalType type : EvalType.values()) {
            Map<String, Double> weights = BuiltinRubrics.defaultWeights(type.getCode());
            assertEquals(1.0, weights.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9,
                    type.getCode() + " 内置权重和应为 1");
        }
    }
}
