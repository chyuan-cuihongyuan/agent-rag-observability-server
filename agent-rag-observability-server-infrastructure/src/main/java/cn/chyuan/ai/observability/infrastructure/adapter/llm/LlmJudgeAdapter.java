package cn.chyuan.ai.observability.infrastructure.adapter.llm;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmJudgePort;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.JudgeVerdict;
import cn.chyuan.ai.observability.domain.evaluate.service.BuiltinRubrics;
import cn.chyuan.ai.observability.domain.evaluate.service.RubricPromptRenderer;
import cn.chyuan.ai.observability.domain.evaluate.service.RubricService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-judge 适配器 — 使用 DeepSeek 通过 Spring AI OpenAI 兼容客户端评判答案质量。
 * <p>
 * 工单 0133 R1 改造：原 9 个硬编码 judge prompt 收编为内置 Rubric 种子（BuiltinRubrics），
 * 本适配器按 Rubric 维度的 judgePrompt 模板渲染（{{query}}/{{reference}}/{{answer}}/{{context}}/
 * {{numberedContext}} 占位符，RubricService 懒加载种子 + 内置兜底），渲染与截断口径与
 * 硬编码时代逐字一致（RubricPromptRendererTest 保真对照）；解析失败/降级的维度记入
 * unknownKeys 供任务级 unknown 占比统计。
 */
@Slf4j
@Component
public class LlmJudgeAdapter implements ILlmJudgePort {

    /** LLM 评判维度总数（unknown 占比分母） */
    private static final List<String> ALL_JUDGE_KEYS = List.copyOf(BuiltinRubrics.JUDGE_TEMPLATES.keySet());

    private final RubricService rubricService;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Value("${observability.eval.judge.enabled:true}")
    private boolean enabled;

    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern HALLUCINATION_PATTERN = Pattern.compile("幻觉比例[：:]\\s*(\\d+(?:\\.\\d+)?)");

    public LlmJudgeAdapter(RubricService rubricService) {
        this.rubricService = rubricService;
    }

    @Override
    public JudgeVerdict judge(String query, String standardAnswer, String actualAnswer, List<String> retrievedChunks) {
        if (!available()) {
            log.debug("LLM评判未启用，返回降级结果");
            return degradedVerdict("LLM未配置或未启用");
        }

        Map<String, String> templates = rubricService.resolveJudgePromptTemplates();
        Map<String, String> vars = RubricPromptRenderer.buildVars(query, standardAnswer, actualAnswer, retrievedChunks);
        List<String> unknownKeys = new ArrayList<>();

        double faithfulness = judgeScore(BuiltinRubrics.KEY_FAITHFULNESS, templates, vars, unknownKeys);
        double relevance = judgeScore(BuiltinRubrics.KEY_RELEVANCY, templates, vars, unknownKeys);
        double completeness = judgeScore(BuiltinRubrics.KEY_COMPLETENESS, templates, vars, unknownKeys);
        double similarity = judgeScore(BuiltinRubrics.KEY_SIMILARITY, templates, vars, unknownKeys);
        // 答案正确性：事实层面的对错（需标准答案，无标准答案则降级为 0）
        double answerCorrectness = (standardAnswer == null || standardAnswer.isEmpty())
                ? 0.0 : judgeScore(BuiltinRubrics.KEY_ANSWER_CORRECTNESS, templates, vars, unknownKeys);
        // 上下文精确率：无检索内容时确定性 0（旧口径短路）
        double contextPrecision = (retrievedChunks == null || retrievedChunks.isEmpty())
                ? 0.0 : judgeScore(BuiltinRubrics.KEY_CONTEXT_PRECISION, templates, vars, unknownKeys);
        // 上下文召回率：需标准答案
        double contextRecall = (standardAnswer == null || standardAnswer.isEmpty())
                ? 0.0 : judgeScore(BuiltinRubrics.KEY_CONTEXT_RECALL, templates, vars, unknownKeys);
        double contextRelevance = judgeScore(BuiltinRubrics.KEY_CONTEXT_RELEVANCE, templates, vars, unknownKeys);
        HallucinationResult hallucination = detectHallucination(templates, vars, unknownKeys);

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
                .unknownKeys(unknownKeys)
                .build();
    }

    @Override
    public String complete(String prompt) {
        if (!available() || prompt == null || prompt.isBlank()) {
            return null;
        }
        try {
            return chatModel.call(new Prompt(new UserMessage(prompt)))
                    .getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("LLM 原始补全失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean available() {
        return enabled && chatModel != null;
    }

    /**
     * 按 Rubric 维度模板渲染并评分 — 解析失败/调用失败时记入 unknownKeys 并返回旧口径默认分 0.5。
     * 模板缺失（维度被禁用）时用内置模板兜底，保证 9 维始终可评。
     */
    private double judgeScore(String key, Map<String, String> templates, Map<String, String> vars,
                              List<String> unknownKeys) {
        String template = templates.get(key);
        if (template == null) {
            template = BuiltinRubrics.JUDGE_TEMPLATES.get(key);
        }
        String prompt = RubricPromptRenderer.render(template, vars);
        try {
            String result = chatModel.call(new Prompt(new UserMessage(prompt)))
                    .getResult().getOutput().getText();
            Matcher matcher = SCORE_PATTERN.matcher(result);
            if (matcher.find()) {
                return clamp(Double.parseDouble(matcher.group(1)));
            }
        } catch (Exception e) {
            log.warn("LLM评分调用失败[{}]: {}", key, e.getMessage());
        }
        unknownKeys.add(key);
        return 0.5;
    }

    /**
     * 检测幻觉 — 答案中包含未在检索内容中出现的信息（模板渲染收编，解析口径不变）。
     */
    private HallucinationResult detectHallucination(Map<String, String> templates, Map<String, String> vars,
                                                    List<String> unknownKeys) {
        String template = templates.getOrDefault(BuiltinRubrics.KEY_HALLUCINATION,
                BuiltinRubrics.JUDGE_TEMPLATES.get(BuiltinRubrics.KEY_HALLUCINATION));
        String prompt = RubricPromptRenderer.render(template, vars);
        try {
            String result = chatModel.call(new Prompt(new UserMessage(prompt)))
                    .getResult().getOutput().getText();
            double rate = extractHallucinationRate(result);
            String detail = truncate(result, 200);
            return new HallucinationResult(rate, detail);
        } catch (Exception e) {
            log.warn("幻觉检测失败: {}", e.getMessage());
            unknownKeys.add(BuiltinRubrics.KEY_HALLUCINATION);
            return new HallucinationResult(0.0, "检测失败: " + e.getMessage());
        }
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
        Matcher score = SCORE_PATTERN.matcher(response);
        if (score.find()) {
            return clamp(Double.parseDouble(score.group(1)));
        }
        return 0.0;
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
                .unknownKeys(new ArrayList<>(ALL_JUDGE_KEYS))
                .build();
    }

    private record HallucinationResult(double rate, String detail) {
    }
}
