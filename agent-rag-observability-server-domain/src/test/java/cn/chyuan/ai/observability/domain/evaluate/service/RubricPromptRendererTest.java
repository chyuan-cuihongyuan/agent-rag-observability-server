package cn.chyuan.ai.observability.domain.evaluate.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rubric prompt 渲染保真测试（工单 0133 R1 验收）：
 * 收编后的内置 {{}} 模板渲染结果与旧 LlmJudgeAdapter 硬编码 %s 格式化逐字一致
 * （同一输入 → 同一 prompt → 同一 LLM 行为 → 综合分同口径）。
 */
@DisplayName("Rubric prompt 渲染保真测试")
public class RubricPromptRendererTest {

    // ===== 旧硬编码模板（%s 格式化，逐字复制自改造前 LlmJudgeAdapter） =====

    private static final String OLD_FAITHFULNESS = """
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

    private static final String OLD_RELEVANCY = """
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

    private static final String OLD_CONTEXT_RELEVANCE = """
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

    private static final String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    @Test
    @DisplayName("faithfulness 收编模板渲染与旧 %s 格式化逐字一致")
    public void testFaithfulnessFidelity() {
        List<String> chunks = List.of("chunk-A 内容", "chunk-B 内容");
        String answer = "这是实际答案";
        String context = String.join("\n---\n", chunks);

        String oldPrompt = String.format(OLD_FAITHFULNESS, truncate(context, 3000), truncate(answer, 1000));
        Map<String, String> vars = RubricPromptRenderer.buildVars("q", "ref", answer, chunks);
        String newPrompt = RubricPromptRenderer.render(BuiltinRubrics.FAITHFULNESS_TEMPLATE, vars);

        assertEquals(oldPrompt, newPrompt, "faithfulness 渲染应与旧口径逐字一致");
    }

    @Test
    @DisplayName("relevancy 收编模板渲染与旧 %s 格式化逐字一致")
    public void testRelevancyFidelity() {
        String query = "用户的问题";
        String answer = "实际的回答";
        String oldPrompt = String.format(OLD_RELEVANCY, truncate(query, 500), truncate(answer, 1000));
        Map<String, String> vars = RubricPromptRenderer.buildVars(query, "ref", answer, List.of());
        assertEquals(oldPrompt, RubricPromptRenderer.render(BuiltinRubrics.RELEVANCY_TEMPLATE, vars),
                "relevancy 渲染应与旧口径逐字一致");
    }

    @Test
    @DisplayName("contextRelevance 收编模板渲染与旧 %s 格式化逐字一致")
    public void testContextRelevanceFidelity() {
        String query = "查询";
        List<String> chunks = List.of("c1", "c2", "c3");
        String context = String.join("\n---\n", chunks);
        String oldPrompt = String.format(OLD_CONTEXT_RELEVANCE, truncate(query, 500), truncate(context, 3000));
        Map<String, String> vars = RubricPromptRenderer.buildVars(query, "ref", "ans", chunks);
        assertEquals(oldPrompt, RubricPromptRenderer.render(BuiltinRubrics.CONTEXT_RELEVANCE_TEMPLATE, vars),
                "contextRelevance 渲染应与旧口径逐字一致");
    }

    @Test
    @DisplayName("contextPrecision 使用编号上下文（{{numberedContext}}），与旧编号口径一致")
    public void testContextPrecisionNumbered() {
        List<String> chunks = List.of("alpha", "beta");
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            numbered.append("[").append(i + 1).append("] ").append(truncate(chunks.get(i), 500)).append("\n");
        }
        Map<String, String> vars = RubricPromptRenderer.buildVars("q", "ref", "ans", chunks);
        String rendered = RubricPromptRenderer.render(BuiltinRubrics.CONTEXT_PRECISION_TEMPLATE, vars);
        assertTrue(rendered.contains("[1] alpha\n[2] beta"), "应含编号上下文: " + rendered);
        assertEquals(numbered.toString(), vars.get("numberedContext"), "numberedContext 变量应与旧口径一致");
    }

    @Test
    @DisplayName("空 chunk 列表 — context 为空串（旧口径一致）")
    public void testEmptyChunks() {
        Map<String, String> vars = RubricPromptRenderer.buildVars("q", "r", "a", null);
        assertEquals("", vars.get("context"), "null chunks 的 context 应为空串");
        assertEquals("", vars.get("numberedContext"));
        String rendered = RubricPromptRenderer.render(BuiltinRubrics.CONTEXT_RECALL_TEMPLATE, vars);
        assertFalse(rendered.contains("{{"), "占位符应全部替换");
    }

    @Test
    @DisplayName("超长输入截断 — 与旧口径阈值一致（answer 1000 / context 3000）")
    public void testTruncation() {
        String longAnswer = "x".repeat(2500);
        String longChunk = "y".repeat(4000);
        Map<String, String> vars = RubricPromptRenderer.buildVars("q", "r", longAnswer, List.of(longChunk));
        assertEquals(1000 + 3, vars.get("answer").length(), "answer 截断到 1000+省略号");
        assertEquals(3000 + 3, vars.get("context").length(), "context 截断到 3000+省略号");
        assertTrue(vars.get("numberedContext").contains("..."), "编号上下文的单条截断生效");
    }

    @Test
    @DisplayName("未识别占位符原样保留")
    public void testUnknownPlaceholderKept() {
        Map<String, String> vars = RubricPromptRenderer.buildVars("q", "r", "a", List.of());
        String rendered = RubricPromptRenderer.render("评估 {{unknownVar}} 与 {{answer}}", vars);
        assertEquals("评估 {{unknownVar}} 与 a", rendered);
    }
}
