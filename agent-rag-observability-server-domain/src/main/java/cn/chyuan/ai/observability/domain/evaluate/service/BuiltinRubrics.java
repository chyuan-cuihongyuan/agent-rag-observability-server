package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.RubricEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricDimension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置 Rubric 种子（工单 0133 R1）— 把 LlmJudgeAdapter 原有 9 个硬编码 judge prompt
 * 收编为内置 Rubric 幂等种子，权重沿用 v2 综合分口径，保证收编后同一输入综合分不变。
 * <p>
 * 模板占位符：{{query}}/{{reference}}/{{answer}}/{{context}}/{{numberedContext}}
 * （与旧 %s 格式化的参数位一一对应，RubricPromptRendererTest 保真对照）。
 * <ul>
 *   <li>builtin-rag-retrieval（RAG_RETRIEVAL）：f1 0.4 / top3 0.2 / mrr 0.2 / ndcg 0.2，确定性维度</li>
 *   <li>builtin-answer-quality（ANSWER_QUALITY）：faithfulness 0.25 / relevancy 0.25 / completeness 0.15
 *       / similarity 0.05 / answerCorrectness 0.2 / hallucination 0.1（反向维度，引擎按 1-rate 计入）</li>
 *   <li>builtin-context-quality（CONTEXT_QUALITY）：contextPrecision 0.4 / contextRecall 0.4 / contextRelevance 0.2</li>
 *   <li>builtin-tool-call（TOOL_CALL）：toolSelection 0.5 / toolParam 0.5，确定性维度</li>
 *   <li>builtin-agent-decision（AGENT_DECISION）：intent 0.3 / branch 0.3 / reasoning 0.4，确定性维度</li>
 * </ul>
 */
public final class BuiltinRubrics {

    private BuiltinRubrics() {
    }

    public static final String KEY_FAITHFULNESS = "faithfulness";
    public static final String KEY_RELEVANCY = "relevancy";
    public static final String KEY_HALLUCINATION = "hallucination";
    public static final String KEY_COMPLETENESS = "completeness";
    public static final String KEY_SIMILARITY = "similarity";
    public static final String KEY_ANSWER_CORRECTNESS = "answerCorrectness";
    public static final String KEY_CONTEXT_PRECISION = "contextPrecision";
    public static final String KEY_CONTEXT_RECALL = "contextRecall";
    public static final String KEY_CONTEXT_RELEVANCE = "contextRelevance";

    // ===== 9 个收编 prompt（文本与旧硬编码模板逐字一致，仅 %s 换 {{}} 占位符） =====

    public static final String FAITHFULNESS_TEMPLATE = """
            请评估以下答案是否忠实于提供的参考资料。

            评分标准：
            - 1.0分：答案完全基于参考资料，没有添加额外信息
            - 0.8分：答案主要基于参考资料，有少量合理推断
            - 0.6分：答案部分基于参考资料，有一些未提及的信息
            - 0.4分：答案与参考资料关联较弱，多为补充信息
            - 0.2分：答案与参考资料基本无关
            - 0.0分：答案完全脱离参考资料

            参考资料：
            {{context}}

            答案：
            {{answer}}

            请只返回一个0-1之间的数字分数，不要解释：
            """;

    public static final String RELEVANCY_TEMPLATE = """
            请评估以下答案是否回答了用户的问题。

            评分标准：
            - 1.0分：完全回答了问题，信息准确且完整
            - 0.8分：基本回答了问题，但缺少一些细节
            - 0.6分：部分回答了问题，但有遗漏
            - 0.4分：回答与问题相关，但没有直接回答
            - 0.2分：回答与问题关联较弱
            - 0.0分：完全没有回答问题

            用户问题：{{query}}

            答案：
            {{answer}}

            请只返回一个0-1之间的数字分数，不要解释：
            """;

    public static final String COMPLETENESS_TEMPLATE = """
            请评估实际答案相对于标准答案的完整性。

            评分标准：
            - 1.0分：实际答案完整覆盖了标准答案的所有要点
            - 0.8分：实际答案覆盖了标准答案的大部分要点
            - 0.6分：实际答案覆盖了标准答案的一半要点
            - 0.4分：实际答案只覆盖了少部分要点
            - 0.2分：实际答案基本没有覆盖标准答案要点
            - 0.0分：完全偏离标准答案

            标准答案：
            {{reference}}

            实际答案：
            {{answer}}

            请只返回一个0-1之间的数字分数，不要解释：
            """;

    public static final String SIMILARITY_TEMPLATE = """
            请评估两个答案的语义相似度。

            评分标准：
            - 1.0分：语义完全一致，只是表述不同
            - 0.8分：语义高度一致，只有细微差别
            - 0.6分：语义基本一致，但有部分差异
            - 0.4分：语义部分一致
            - 0.2分：语义有较大差异
            - 0.0分：语义完全不同

            答案A：
            {{reference}}

            答案B：
            {{answer}}

            请只返回一个0-1之间的数字分数，不要解释：
            """;

    public static final String HALLUCINATION_TEMPLATE = """
            请检测答案中是否存在"幻觉"（即未在参考资料中出现的信息）。

            分析要求：
            1. 逐句检查答案中的每个事实性陈述
            2. 判断每个陈述是否有参考资料支撑
            3. 计算幻觉比例（幻觉语句数/总语句数）

            参考资料：
            {{context}}

            答案：
            {{answer}}

            请按以下格式返回：
            幻觉比例: 0.0-1.0之间的数字
            幻觉内容: 列出具体的幻觉语句（如有）

            示例输出：
            幻觉比例: 0.2
            幻觉内容:
            - "该功能于2020年发布"（参考资料未提及发布时间）
            """;

    public static final String CONTEXT_PRECISION_TEMPLATE = """
            请评估检索到的每个上下文片段对回答用户问题的有用程度（上下文精确率）。

            用户问题：{{query}}

            检索到的上下文片段（已编号）：
            {{numberedContext}}

            分析要求：
            1. 逐条判断每个编号片段是否对回答该问题相关且有用（1=相关有用, 0=无关或噪声）
            2. 计算上下文精确率 = 相关片段数 / 总片段数

            请只返回一个0-1之间的数字（相关片段占比），不要解释：
            """;

    public static final String CONTEXT_RECALL_TEMPLATE = """
            请评估检索到的上下文能否支撑标准答案的每个要点（上下文召回率）。

            标准答案：
            {{reference}}

            检索到的上下文：
            {{context}}

            分析要求：
            1. 将标准答案拆分为若干事实要点
            2. 逐个判断每个要点能否从检索上下文中找到支撑（1=可推断, 0=无法推断）
            3. 计算上下文召回率 = 可推断要点数 / 总要点数

            请只返回一个0-1之间的数字（可推断要点占比），不要解释：
            """;

    public static final String CONTEXT_RELEVANCE_TEMPLATE = """
            请评估检索到的上下文与用户问题的整体相关度（上下文相关性）。

            评分标准：
            - 1.0分：上下文完全切题，全是相关信息
            - 0.8分：上下文大部分相关，少量冗余
            - 0.6分：上下文部分相关，存在一定噪声
            - 0.4分：上下文相关性较弱，多为边缘信息
            - 0.2分：上下文基本与问题无关
            - 0.0分：上下文完全无关

            用户问题：{{query}}

            检索到的上下文：
            {{context}}

            请只返回一个0-1之间的数字分数，不要解释：
            """;

    public static final String ANSWER_CORRECTNESS_TEMPLATE = """
            请评估实际答案相对标准答案的事实正确性（答案正确性，区别于覆盖率）。

            评分标准：
            - 1.0分：实际答案事实完全正确，无任何错误陈述
            - 0.8分：实际答案基本正确，有极少量不够严谨的表述
            - 0.6分：实际答案部分正确，存在少量事实错误
            - 0.4分：实际答案正确性一般，有明显事实错误
            - 0.2分：实际答案大部分事实错误
            - 0.0分：实际答案完全错误

            标准答案：{{reference}}

            实际答案：{{answer}}

            请只返回一个0-1之间的数字分数，不要解释：
            """;

    /** 9 个 LLM 评判维度的内置模板（key → 模板），LlmJudgeAdapter 仓储缺失时的兜底 */
    public static final Map<String, String> JUDGE_TEMPLATES = buildJudgeTemplates();

    private static Map<String, String> buildJudgeTemplates() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(KEY_FAITHFULNESS, FAITHFULNESS_TEMPLATE);
        m.put(KEY_RELEVANCY, RELEVANCY_TEMPLATE);
        m.put(KEY_HALLUCINATION, HALLUCINATION_TEMPLATE);
        m.put(KEY_COMPLETENESS, COMPLETENESS_TEMPLATE);
        m.put(KEY_SIMILARITY, SIMILARITY_TEMPLATE);
        m.put(KEY_ANSWER_CORRECTNESS, ANSWER_CORRECTNESS_TEMPLATE);
        m.put(KEY_CONTEXT_PRECISION, CONTEXT_PRECISION_TEMPLATE);
        m.put(KEY_CONTEXT_RECALL, CONTEXT_RECALL_TEMPLATE);
        m.put(KEY_CONTEXT_RELEVANCE, CONTEXT_RELEVANCE_TEMPLATE);
        return m;
    }

    // ===== 5 个内置 Rubric 定义 =====

    /** 全部内置 Rubric（懒加载幂等种子源） */
    public static List<RubricEntity> builtinRubrics() {
        List<RubricEntity> list = new ArrayList<>();
        list.add(rubric("builtin-rag-retrieval", "内置-RAG检索评测标准", "RAG_RETRIEVAL", List.of(
                dim("f1", "F1分数", 0.4),
                dim("top3HitRate", "Top3命中率", 0.2),
                dim("mrr", "MRR", 0.2),
                dim("ndcg", "NDCG", 0.2))));
        list.add(rubric("builtin-answer-quality", "内置-答案质量评测标准", "ANSWER_QUALITY", List.of(
                judgeDim(KEY_FAITHFULNESS, "忠实度", 0.25, FAITHFULNESS_TEMPLATE),
                judgeDim(KEY_RELEVANCY, "答案相关性", 0.25, RELEVANCY_TEMPLATE),
                judgeDim(KEY_COMPLETENESS, "完整性", 0.15, COMPLETENESS_TEMPLATE),
                judgeDim(KEY_SIMILARITY, "语义相似度", 0.05, SIMILARITY_TEMPLATE),
                judgeDim(KEY_ANSWER_CORRECTNESS, "答案正确性", 0.2, ANSWER_CORRECTNESS_TEMPLATE),
                judgeDim(KEY_HALLUCINATION, "幻觉（反向）", 0.1, HALLUCINATION_TEMPLATE))));
        list.add(rubric("builtin-context-quality", "内置-上下文质量评测标准", "CONTEXT_QUALITY", List.of(
                judgeDim(KEY_CONTEXT_PRECISION, "上下文精确率", 0.4, CONTEXT_PRECISION_TEMPLATE),
                judgeDim(KEY_CONTEXT_RECALL, "上下文召回率", 0.4, CONTEXT_RECALL_TEMPLATE),
                judgeDim(KEY_CONTEXT_RELEVANCE, "上下文相关性", 0.2, CONTEXT_RELEVANCE_TEMPLATE))));
        list.add(rubric("builtin-tool-call", "内置-工具调用评测标准", "TOOL_CALL", List.of(
                dim("toolSelection", "工具选择正确率", 0.5),
                dim("toolParam", "工具参数正确率", 0.5))));
        list.add(rubric("builtin-agent-decision", "内置-Agent决策评测标准", "AGENT_DECISION", List.of(
                dim("intent", "意图识别正确率", 0.3),
                dim("branch", "分支选择正确率", 0.3),
                dim("reasoning", "推理质量", 0.4))));
        return list;
    }

    /**
     * v2 口径兜底权重表（仓储不可用/未种子时使用）。
     * 未知 evalType 用 DEFAULT_WEIGHTS（旧默认分支 检索0.4+上下文0.2+质量0.4 的平铺展开，
     * 输入分数均在 [0,1] 时与旧层级公式严格等价）。
     */
    public static Map<String, Double> defaultWeights(String evalType) {
        Map<String, Double> m = new LinkedHashMap<>();
        if (evalType == null) {
            evalType = "";
        }
        switch (evalType) {
            case "RAG_RETRIEVAL" -> {
                m.put("f1", 0.4);
                m.put("top3HitRate", 0.2);
                m.put("mrr", 0.2);
                m.put("ndcg", 0.2);
            }
            case "ANSWER_QUALITY" -> {
                m.put(KEY_FAITHFULNESS, 0.25);
                m.put(KEY_RELEVANCY, 0.25);
                m.put(KEY_COMPLETENESS, 0.15);
                m.put(KEY_SIMILARITY, 0.05);
                m.put(KEY_ANSWER_CORRECTNESS, 0.2);
                m.put(KEY_HALLUCINATION, 0.1);
            }
            case "CONTEXT_QUALITY" -> {
                m.put(KEY_CONTEXT_PRECISION, 0.4);
                m.put(KEY_CONTEXT_RECALL, 0.4);
                m.put(KEY_CONTEXT_RELEVANCE, 0.2);
            }
            case "TOOL_CALL" -> {
                m.put("toolSelection", 0.5);
                m.put("toolParam", 0.5);
            }
            case "AGENT_DECISION" -> {
                m.put("intent", 0.3);
                m.put("branch", 0.3);
                m.put("reasoning", 0.4);
            }
            default -> m.putAll(DEFAULT_WEIGHTS);
        }
        return m;
    }

    /** 旧 computeOverall 默认分支（检索 0.4 + 上下文 0.2 + 质量 0.4）的平铺展开，和为 1 */
    private static final Map<String, Double> DEFAULT_WEIGHTS = Map.of(
            "f1", 0.16, "top3HitRate", 0.08, "mrr", 0.08, "ndcg", 0.08,
            KEY_CONTEXT_PRECISION, 0.08, KEY_CONTEXT_RECALL, 0.08, KEY_CONTEXT_RELEVANCE, 0.04,
            KEY_FAITHFULNESS, 0.14, KEY_RELEVANCY, 0.14, KEY_HALLUCINATION, 0.12);

    private static RubricEntity rubric(String rubricId, String name, String evalType, List<RubricDimension> dims) {
        return RubricEntity.builder()
                .rubricId(rubricId).name(name).evalType(evalType)
                .version(1).dimensions(dims)
                .enabled(Boolean.TRUE).builtin(Boolean.TRUE)
                .build();
    }

    /** 确定性指标维度（无 judgePrompt，分数由调用方注入） */
    private static RubricDimension dim(String key, String label, double weight) {
        return RubricDimension.builder().key(key).label(label).weight(weight)
                .judgePrompt(null).binary(Boolean.FALSE).build();
    }

    /** LLM 评判维度（连续分口径，收编自旧硬编码 prompt） */
    private static RubricDimension judgeDim(String key, String label, double weight, String template) {
        return RubricDimension.builder().key(key).label(label).weight(weight)
                .judgePrompt(template).binary(Boolean.FALSE).build();
    }
}
