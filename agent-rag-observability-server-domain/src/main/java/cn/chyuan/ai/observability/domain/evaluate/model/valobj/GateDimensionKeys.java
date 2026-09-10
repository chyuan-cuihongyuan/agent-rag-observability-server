package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

import java.util.Set;

/**
 * 门禁可用维度 key 常量（工单 0136 R4）— 安全维度/分数阈值的合法 key 全集：
 * 与 EvalExecutionService.buildMetricScores 注入的维度 key（即 Rubric 维度 key）严格对齐，
 * 外加两个任务级汇总 key（overall 综合分均值 / passRate 通过率，仅允许出现在 score_thresholds）。
 * <p>
 * 方向口径统一为<b>正向分</b>（越高越好）：hallucination 的安全分 = 1 - 幻觉率，
 * 门禁配置 {"hallucination": 0.8} 语义等价于「幻觉率均值上限 0.2」。
 */
public final class GateDimensionKeys {

    private GateDimensionKeys() {
    }

    /** 任务级汇总 key：综合分均值（仅 score_thresholds 可用） */
    public static final String OVERALL = "overall";
    /** 任务级汇总 key：Pass@k 通过率（仅 score_thresholds 可用） */
    public static final String PASS_RATE = "passRate";

    /** Rubric/指标维度 key 全集（safety_dims 与 score_thresholds 均可用） */
    public static final Set<String> DIMENSION_KEYS = Set.of(
            // 检索确定性指标
            "f1", "top3HitRate", "mrr", "ndcg",
            // LLM 评判维度（R1 Rubric 维度 key，正向分口径；hallucination = 1 - 幻觉率）
            "faithfulness", "relevancy", "hallucination", "completeness", "similarity",
            "answerCorrectness", "contextPrecision", "contextRecall", "contextRelevance",
            // 工具调用
            "toolSelection", "toolParam",
            // Agent 决策
            "intent", "branch", "reasoning"
    );

    public static boolean isDimensionKey(String key) {
        return key != null && DIMENSION_KEYS.contains(key);
    }

    public static boolean isSummaryKey(String key) {
        return OVERALL.equals(key) || PASS_RATE.equals(key);
    }
}
