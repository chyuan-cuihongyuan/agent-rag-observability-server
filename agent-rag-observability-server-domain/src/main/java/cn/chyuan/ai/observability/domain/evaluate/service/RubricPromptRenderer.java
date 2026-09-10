package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricDimension;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rubric 评判 prompt 渲染器（工单 0133 R1）— 把维度 judgePrompt 模板的
 * {{query}}/{{reference}}/{{answer}}/{{context}}/{{numberedContext}} 占位符替换为实参。
 * <p>
 * 截断口径沿用旧 LlmJudgeAdapter 硬编码时代：query 500 / answer 1000 / reference 1000 /
 * context 3000 / numberedContext 每条 500 总 3000，保证收编后同一输入渲染文本不变。
 */
public final class RubricPromptRenderer {

    private static final Pattern VAR = Pattern.compile("\\{\\{(query|reference|answer|context|numberedContext)}}");

    private RubricPromptRenderer() {
    }

    /**
     * 构造渲染变量集（含旧口径截断）。
     *
     * @param query      用户查询
     * @param reference  标准答案（reference/标准答案）
     * @param answer     实际答案
     * @param chunks     实际检索 chunk 列表（空列表时 context 为空串）
     */
    public static Map<String, String> buildVars(String query, String reference, String answer, List<String> chunks) {
        String context = chunks == null || chunks.isEmpty() ? "" : String.join("\n---\n", chunks);
        Map<String, String> vars = new java.util.LinkedHashMap<>();
        vars.put(RubricDimension.VAR_QUERY, truncate(query, 500));
        vars.put(RubricDimension.VAR_REFERENCE, truncate(reference, 1000));
        vars.put(RubricDimension.VAR_ANSWER, truncate(answer, 1000));
        vars.put(RubricDimension.VAR_CONTEXT, truncate(context, 3000));
        vars.put(RubricDimension.VAR_NUMBERED_CONTEXT, truncate(numbered(chunks), 3000));
        return vars;
    }

    /** 渲染模板：未识别的 {{xxx}} 占位符原样保留（便于发现模板笔误） */
    public static String render(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = vars.getOrDefault(m.group(1), m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 检索 chunk 编号列表（[1] xxx\n[2] yyy，与旧 evaluateContextPrecision 口径一致） */
    private static String numbered(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            numbered.append("[").append(i + 1).append("] ")
                    .append(truncate(chunks.get(i), 500)).append("\n");
        }
        return numbered.toString();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
