package cn.chyuan.ai.observability.infrastructure.adapter.llm;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmJudgePort;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.JudgeVerdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-judge 适配器 — 使用 DeepSeek 通过 Spring AI OpenAI 兼容客户端评判答案质量。
 * 复用 aggregation-support-agent AnswerQualityEvaluator 已验证的 prompt 与解析逻辑。
 */
@Slf4j
@Component
public class LlmJudgeAdapter implements ILlmJudgePort {

    @Autowired(required = false)
    private ChatModel chatModel;

    @Value("${observability.eval.judge.enabled:true}")
    private boolean enabled;

    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern HALLUCINATION_PATTERN = Pattern.compile("幻觉比例[：:]\\s*(\\d+(?:\\.\\d+)?)");

    // 预编译 prompt 模板，避免每次调用时重新格式化
    private static final String FAITHFULNESS_PROMPT_TEMPLATE = """
                请评估以下答案是否忠实于提供的参考资料。

                评分标准：
                - 1.0分：答案完全基于参考资料，没有添加额外信息
                - 0.8分：答案主要基于参考资料，有少量合理推断
                - 0.6分：答案部分基于参考资料，有一些未提及的信息
                - 0.4分：答案与参考资料关联较弱，多为补充信息
                - 0.2分：答案与参考资料基本无关
                - 0.0分：答案完全脱离参考资料

                参考资料：
                %s

                答案：
                %s

                请只返回一个0-1之间的数字分数，不要解释：
                """;

    private static final String RELEVANCY_PROMPT_TEMPLATE = """
                请评估以下答案是否回答了用户的问题。

                评分标准：
                - 1.0分：完全回答了问题，信息准确且完整
                - 0.8分：基本回答了问题，但缺少一些细节
                - 0.6分：部分回答了问题，但有遗漏
                - 0.4分：回答与问题相关，但没有直接回答
                - 0.2分：回答与问题关联较弱
                - 0.0分：完全没有回答问题

                用户问题：%s

                答案：
                %s

                请只返回一个0-1之间的数字分数，不要解释：
                """;

    private static final String COMPLETENESS_PROMPT_TEMPLATE = """
                请评估实际答案相对于标准答案的完整性。

                评分标准：
                - 1.0分：实际答案完整覆盖了标准答案的所有要点
                - 0.8分：实际答案覆盖了标准答案的大部分要点
                - 0.6分：实际答案覆盖了标准答案的一半要点
                - 0.4分：实际答案只覆盖了少部分要点
                - 0.2分：实际答案基本没有覆盖标准答案要点
                - 0.0分：完全偏离标准答案

                标准答案：
                %s

                实际答案：
                %s

                请只返回一个0-1之间的数字分数，不要解释：
                """;

    private static final String SIMILARITY_PROMPT_TEMPLATE = """
                请评估两个答案的语义相似度。

                评分标准：
                - 1.0分：语义完全一致，只是表述不同
                - 0.8分：语义高度一致，只有细微差别
                - 0.6分：语义基本一致，但有部分差异
                - 0.4分：语义部分一致
                - 0.2分：语义有较大差异
                - 0.0分：语义完全不同

                答案A：
                %s

                答案B：
                %s

                请只返回一个0-1之间的数字分数，不要解释：
                """;

    private static final String HALLUCINATION_PROMPT_TEMPLATE = """
                请检测答案中是否存在"幻觉"（即未在参考资料中出现的信息）。

                分析要求：
                1. 逐句检查答案中的每个事实性陈述
                2. 判断每个陈述是否有参考资料支撑
                3. 计算幻觉比例（幻觉语句数/总语句数）

                参考资料：
                %s

                答案：
                %s

                请按以下格式返回：
                幻觉比例: 0.0-1.0之间的数字
                幻觉内容: 列出具体的幻觉语句（如有）

                示例输出：
                幻觉比例: 0.2
                幻觉内容:
                - "该功能于2020年发布"（参考资料未提及发布时间）
                """;

    // ===== 上下文维度 + 答案正确性 prompt（RAGAS 式，LLM-as-Judge） =====

    private static final String CONTEXT_PRECISION_PROMPT_TEMPLATE = """
                请评估检索到的每个上下文片段对回答用户问题的有用程度（上下文精确率）。

                用户问题：%s

                检索到的上下文片段（已编号）：
                %s

                分析要求：
                1. 逐条判断每个编号片段是否对回答该问题相关且有用（1=相关有用, 0=无关或噪声）
                2. 计算上下文精确率 = 相关片段数 / 总片段数

                请只返回一个0-1之间的数字（相关片段占比），不要解释：
                """;

    private static final String CONTEXT_RECALL_PROMPT_TEMPLATE = """
                请评估检索到的上下文能否支撑标准答案的每个要点（上下文召回率）。

                标准答案：
                %s

                检索到的上下文：
                %s

                分析要求：
                1. 将标准答案拆分为若干事实要点
                2. 逐个判断每个要点能否从检索上下文中找到支撑（1=可推断, 0=无法推断）
                3. 计算上下文召回率 = 可推断要点数 / 总要点数

                请只返回一个0-1之间的数字（可推断要点占比），不要解释：
                """;

    private static final String CONTEXT_RELEVANCE_PROMPT_TEMPLATE = """
                请评估检索到的上下文与用户问题的整体相关度（上下文相关性）。

                评分标准：
                - 1.0分：上下文完全切题，全是相关信息
                - 0.8分：上下文大部分相关，少量冗余
                - 0.6分：上下文部分相关，存在一定噪声
                - 0.4分：上下文相关性较弱，多为边缘信息
                - 0.2分：上下文基本与问题无关
                - 0.0分：上下文完全无关

                用户问题：%s

                检索到的上下文：
                %s

                请只返回一个0-1之间的数字分数，不要解释：
                """;

    private static final String ANSWER_CORRECTNESS_PROMPT_TEMPLATE = """
                请评估实际答案相对标准答案的事实正确性（答案正确性，区别于覆盖率）。

                评分标准：
                - 1.0分：实际答案事实完全正确，无任何错误陈述
                - 0.8分：实际答案基本正确，有极少量不够严谨的表述
                - 0.6分：实际答案部分正确，存在少量事实错误
                - 0.4分：实际答案正确性一般，有明显事实错误
                - 0.2分：实际答案大部分事实错误
                - 0.0分：实际答案完全错误

                标准答案：%s

                实际答案：%s

                请只返回一个0-1之间的数字分数，不要解释：
                """;

    @Override
    public JudgeVerdict judge(String query, String standardAnswer, String actualAnswer, List<String> retrievedChunks) {
        if (!available()) {
            log.debug("LLM评判未启用，返回降级结果");
            return degradedVerdict("LLM未配置或未启用");
        }

        String context = retrievedChunks == null || retrievedChunks.isEmpty()
                ? "" : String.join("\n---\n", retrievedChunks);

        try {
            double faithfulness = evaluateFaithfulness(actualAnswer, context);
            double relevance = evaluateRelevancy(query, actualAnswer);
            HallucinationResult hallucination = detectHallucination(actualAnswer, context);
            double completeness = evaluateCompleteness(standardAnswer, actualAnswer);
            double similarity = evaluateSimilarity(standardAnswer, actualAnswer);
            // 答案正确性：事实层面的对错（需标准答案，无标准答案则降级为 0）
            double answerCorrectness = (standardAnswer == null || standardAnswer.isEmpty())
                    ? 0.0 : evaluateAnswerCorrectness(standardAnswer, actualAnswer);
            // 上下文维度（RAGAS 式，需检索内容）
            double contextPrecision = evaluateContextPrecision(query, retrievedChunks);
            double contextRecall = (standardAnswer == null || standardAnswer.isEmpty())
                    ? 0.0 : evaluateContextRecall(standardAnswer, context);
            double contextRelevance = evaluateContextRelevance(query, context);

            return JudgeVerdict.builder()
                    .faithfulness(round(faithfulness))
                    .relevance(round(relevance))
                    .hallucinationRate(round(hallucination.rate))
                    .completeness(round(completeness))
                    .similarity(round(similarity))
                    .answerCorrectness(round(answerCorrectness))
                    .contextPrecision(round(contextPrecision))
                    .contextRecall(round(contextRecall))
                    .contextRelevance(round(contextRelevance))
                    .detail(String.format("幻觉检测: %s", hallucination.detail))
                    .degraded(false)
                    .build();
        } catch (Exception e) {
            log.warn("LLM评判失败, query={}, err={}", query, e.getMessage());
            return degradedVerdict("评判失败: " + e.getMessage());
        }
    }

    @Override
    public boolean available() {
        return enabled && chatModel != null;
    }

    /**
     * 评估忠实度 — 答案是否基于检索内容
     */
    private double evaluateFaithfulness(String answer, String context) {
        String prompt = String.format(FAITHFULNESS_PROMPT_TEMPLATE,
                truncate(context, 3000), truncate(answer, 1000));
        return callLLMForScore(prompt, 0.5);
    }

    /**
     * 评估相关度 — 答案是否回答了问题
     */
    private double evaluateRelevancy(String query, String answer) {
        String prompt = String.format(RELEVANCY_PROMPT_TEMPLATE,
                truncate(query, 500), truncate(answer, 1000));
        return callLLMForScore(prompt, 0.5);
    }

    /**
     * 评估完整性 — 答案覆盖标准答案要点的程度
     */
    private double evaluateCompleteness(String standardAnswer, String actualAnswer) {
        String prompt = String.format(COMPLETENESS_PROMPT_TEMPLATE,
                truncate(standardAnswer, 1000), truncate(actualAnswer, 1000));
        return callLLMForScore(prompt, 0.5);
    }

    /**
     * 评估语义相似度 — 答案与标准答案的语义接近度
     */
    private double evaluateSimilarity(String standardAnswer, String actualAnswer) {
        String prompt = String.format(SIMILARITY_PROMPT_TEMPLATE,
                truncate(standardAnswer, 1000), truncate(actualAnswer, 1000));
        return callLLMForScore(prompt, 0.5);
    }

    // ===== 上下文维度 + 答案正确性评估 =====

    /**
     * 上下文精确率 — 逐条判定检索 chunk 对回答 query 是否相关（一个 prompt 批量处理，控成本）
     */
    private double evaluateContextPrecision(String query, List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0.0;
        }
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            numbered.append("[").append(i + 1).append("] ")
                    .append(truncate(chunks.get(i), 500)).append("\n");
        }
        String prompt = String.format(CONTEXT_PRECISION_PROMPT_TEMPLATE,
                truncate(query, 500), truncate(numbered.toString(), 3000));
        return callLLMForScore(prompt, 0.5);
    }

    /**
     * 上下文召回率 — 标准答案要点能否从检索上下文推断
     */
    private double evaluateContextRecall(String standardAnswer, String context) {
        String prompt = String.format(CONTEXT_RECALL_PROMPT_TEMPLATE,
                truncate(standardAnswer, 1000), truncate(context, 3000));
        return callLLMForScore(prompt, 0.5);
    }

    /**
     * 上下文相关性 — 检索内容与 query 的整体相关度
     */
    private double evaluateContextRelevance(String query, String context) {
        String prompt = String.format(CONTEXT_RELEVANCE_PROMPT_TEMPLATE,
                truncate(query, 500), truncate(context, 3000));
        return callLLMForScore(prompt, 0.5);
    }

    /**
     * 答案正确性 — 实际答案相对标准答案的事实正确性
     */
    private double evaluateAnswerCorrectness(String standardAnswer, String actualAnswer) {
        String prompt = String.format(ANSWER_CORRECTNESS_PROMPT_TEMPLATE,
                truncate(standardAnswer, 1000), truncate(actualAnswer, 1000));
        return callLLMForScore(prompt, 0.5);
    }

    /**
     * 检测幻觉 — 答案中包含未在检索内容中出现的信息
     */
    private HallucinationResult detectHallucination(String answer, String context) {
        String prompt = String.format(HALLUCINATION_PROMPT_TEMPLATE,
                truncate(context, 3000), truncate(answer, 1000));

        try {
            String result = chatModel.call(new Prompt(new UserMessage(prompt)))
                    .getResult().getOutput().getText();

            double rate = extractHallucinationRate(result);
            String detail = truncate(result, 200);
            return new HallucinationResult(rate, detail);
        } catch (Exception e) {
            log.warn("幻觉检测失败: {}", e.getMessage());
            return new HallucinationResult(0.0, "检测失败: " + e.getMessage());
        }
    }

    /**
     * 调用LLM获取分数
     */
    private double callLLMForScore(String prompt, double defaultScore) {
        try {
            String result = chatModel.call(new Prompt(new UserMessage(prompt)))
                    .getResult().getOutput().getText();
            return extractScore(result, defaultScore);
        } catch (Exception e) {
            log.warn("LLM评分调用失败: {}", e.getMessage());
            return defaultScore;
        }
    }

    /**
     * 从LLM响应中提取分数
     */
    private double extractScore(String response, double defaultScore) {
        Matcher matcher = SCORE_PATTERN.matcher(response);
        if (matcher.find()) {
            double score = Double.parseDouble(matcher.group(1));
            return clamp(score);
        }
        return defaultScore;
    }

    /**
     * 从LLM响应中提取幻觉比例
     */
    private double extractHallucinationRate(String response) {
        Matcher matcher = HALLUCINATION_PATTERN.matcher(response);
        if (matcher.find()) {
            double rate = Double.parseDouble(matcher.group(1));
            return clamp(rate);
        }
        // 降级尝试直接提取第一个数字
        return extractScore(response, 0.0);
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private JudgeVerdict degradedVerdict(String reason) {
        return JudgeVerdict.builder()
                .faithfulness(0.0)
                .relevance(0.0)
                .hallucinationRate(0.0)
                .completeness(0.0)
                .similarity(0.0)
                .answerCorrectness(0.0)
                .contextPrecision(0.0)
                .contextRecall(0.0)
                .contextRelevance(0.0)
                .detail(reason)
                .degraded(true)
                .build();
    }

    private record HallucinationResult(double rate, String detail) {
    }
}
