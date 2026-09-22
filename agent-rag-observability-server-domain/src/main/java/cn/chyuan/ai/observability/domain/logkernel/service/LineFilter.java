package cn.chyuan.ai.observability.domain.logkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行过滤与格式化（工单 0576 BQ4，loki line filter/label_format 思想）。
 * 行过滤表达式（提取字段比较保留丢弃，缺字段丢弃）/label_format 标签重命名/
 * 派生字段计算/管道阶段顺序组合语义。
 */
public final class LineFilter {

    /** 过滤表达式：字段 + 算子 + 值（op ∈ =,!=,>,<,>=,<=;> < 按数值） */
    public record Expression(String field, String op, String value) {
    }

    /** 行过滤：命中保留（缺字段视为不命中） */
    public boolean keep(ParserStages.ParsedLine parsed, Expression expression) {
        String actual = parsed.extracted().get(expression.field());
        if (actual == null) {
            return false;
        }
        return switch (expression.op()) {
            case "=" -> actual.equals(expression.value());
            case "!=" -> !actual.equals(expression.value());
            case ">", "<", ">=", "<=" -> compareNumeric(actual, expression.op(), expression.value());
            default -> throw new IllegalArgumentException("非法过滤算子: " + expression.op());
        };
    }

    /** 多表达式 AND 组合 */
    public boolean keepAll(ParserStages.ParsedLine parsed, List<Expression> expressions) {
        for (Expression expression : expressions) {
            if (!keep(parsed, expression)) {
                return false;
            }
        }
        return true;
    }

    /** 批量过滤（保持输入序） */
    public List<ParserStages.ParsedLine> filter(List<ParserStages.ParsedLine> lines,
            List<Expression> expressions) {
        List<ParserStages.ParsedLine> out = new ArrayList<>();
        for (ParserStages.ParsedLine line : lines) {
            if (keepAll(line, expressions)) {
                out.add(line);
            }
        }
        return List.copyOf(out);
    }

    /**
     * label_format：标签重命名（renames oldLabel→newLabel）+ 派生字段
     * （derivedLabel → 含 {field} 占位的模板求值）。
     */
    public Map<String, String> labelFormat(Map<String, String> labels, Map<String, String> renames,
            Map<String, String> derivedTemplates, ParserStages.ParsedLine parsed) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            out.put(renames.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
        }
        for (Map.Entry<String, String> template : derivedTemplates.entrySet()) {
            String value = template.getValue();
            for (Map.Entry<String, String> field : parsed.extracted().entrySet()) {
                value = value.replace("{" + field.getKey() + "}", field.getValue());
            }
            out.put(template.getKey(), value);
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private boolean compareNumeric(String actual, String op, String expected) {
        double a;
        double b;
        try {
            a = Double.parseDouble(actual);
            b = Double.parseDouble(expected);
        } catch (NumberFormatException e) {
            return false;
        }
        return switch (op) {
            case ">" -> a > b;
            case "<" -> a < b;
            case ">=" -> a >= b;
            case "<=" -> a <= b;
            default -> false;
        };
    }
}
