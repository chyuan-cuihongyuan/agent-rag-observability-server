package cn.chyuan.ai.observability.domain.resilience.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据质量期望规则值对象（工单 0221 AD2，借鉴 Great Expectations）—
 * 断言类型：NOT_NULL / RANGE（min,max）/ ENUM（values 逗号分隔）/ FRESHNESS（maxAgeMs）。
 */
public record QualityRule(Long id, String name, String target, String field, String type,
        java.util.Map<String, String> params, boolean enabled) {

    public static final String TYPE_NOT_NULL = "NOT_NULL";
    public static final String TYPE_RANGE = "RANGE";
    public static final String TYPE_ENUM = "ENUM";
    public static final String TYPE_FRESHNESS = "FRESHNESS";

    private static final java.util.Set<String> LEGAL = java.util.Set.of(
            TYPE_NOT_NULL, TYPE_RANGE, TYPE_ENUM, TYPE_FRESHNESS);

    public QualityRule {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("规则名不能为空");
        }
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        if (type == null || !LEGAL.contains(type)) {
            throw new IllegalArgumentException("非法断言类型: " + type);
        }
        params = params == null ? java.util.Map.of() : java.util.Map.copyOf(params);
    }

    /** 解析范围参数 [min,max] */
    public double[] rangeBounds() {
        double min = Double.parseDouble(params.getOrDefault("min", "-Infinity"));
        double max = Double.parseDouble(params.getOrDefault("max", "Infinity"));
        return new double[]{min, max};
    }

    /** 解析枚举集合 */
    public java.util.Set<String> enumValues() {
        String raw = params.getOrDefault("values", "");
        return java.util.Arrays.stream(raw.split("[,，]")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet());
    }

    /** 解析新鲜度最大年龄（毫秒） */
    public long maxAgeMs() {
        return Long.parseLong(params.getOrDefault("maxAgeMs", "86400000"));
    }
}
