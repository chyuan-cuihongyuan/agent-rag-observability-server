package cn.chyuan.ai.observability.domain.rulekernel.service;

import java.util.Map;

/**
 * 模板渲染（工单 1041 EM5，prometheus template 思想）。
 * {{$labels.x}} 与 {{$value}} 注入/缺失标签空串/注解渲染。
 */
public final class Templates {

    private Templates() {
    }

    /** 渲染：{{$labels.key}} 取标签（缺失空串）；{{$value}} 取值；其余原样 */
    public static String render(String template, Map<String, String> labels, double value) {
        if (template == null) {
            throw new IllegalArgumentException("模板为空");
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            int start = template.indexOf("{{", i);
            if (start < 0) {
                out.append(template, i, template.length());
                break;
            }
            int end = template.indexOf("}}", start);
            if (end < 0) {
                out.append(template, i, template.length());
                break;
            }
            out.append(template, i, start);
            String expr = template.substring(start + 2, end).trim();
            out.append(resolve(expr, labels, value));
            i = end + 2;
        }
        return out.toString();
    }

    private static String resolve(String expr, Map<String, String> labels, double value) {
        if (expr.equals("$value")) {
            return format(value);
        }
        if (expr.startsWith("$labels.")) {
            String key = expr.substring("$labels.".length());
            return labels.getOrDefault(key, "");
        }
        return "{{" + expr + "}}";
    }

    static String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
