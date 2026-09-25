package cn.chyuan.ai.observability.domain.designkernel.service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 设计令牌尺度表与主题变量（工单 0878/0883 CZ1·CZ6，tailwind 思想）。
 * 间距尺度 4px 基可配/色阶与字重表/CSS 变量导出/暗色主题覆盖。
 */
public final class DesignTokens {

    /** 色彩令牌：名称-阶 → hex */
    public static final Map<String, String> PALETTE = Map.ofEntries(
            Map.entry("red-400", "#f87171"), Map.entry("red-500", "#ef4444"), Map.entry("red-600", "#dc2626"),
            Map.entry("green-400", "#4ade80"), Map.entry("green-500", "#22c55e"), Map.entry("green-600", "#16a34a"),
            Map.entry("blue-400", "#60a5fa"), Map.entry("blue-500", "#3b82f6"), Map.entry("blue-600", "#2563eb"),
            Map.entry("slate-100", "#f1f5f9"), Map.entry("slate-500", "#64748b"), Map.entry("slate-900", "#0f172a"));

    public static final List<String> COLOR_STEPS = List.of("50", "100", "200", "300", "400", "500", "600", "700",
            "800", "900", "950");
    public static final List<String> FONT_WEIGHTS = List.of("100", "200", "300", "400", "500", "600", "700", "800",
            "900");

    private final int spacingBase;
    private final Map<String, String> darkOverrides;

    public DesignTokens(int spacingBase, Map<String, String> darkOverrides) {
        if (spacingBase <= 0) {
            throw new IllegalArgumentException("间距基须为正");
        }
        this.spacingBase = spacingBase;
        this.darkOverrides = Map.copyOf(darkOverrides);
    }

    public DesignTokens(int spacingBase) {
        this(spacingBase, Map.of());
    }

    public DesignTokens() {
        this(4, Map.of());
    }

    public int spacingBase() {
        return spacingBase;
    }

    /** 间距令牌：数值键 × 基（0.5 半步），负数拒绝 */
    public int spacingPx(String key) {
        double v;
        try {
            v = Double.parseDouble(key);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法间距令牌: " + key);
        }
        if (v < 0) {
            throw new IllegalArgumentException("间距为负: " + key);
        }
        return (int) Math.round(v * spacingBase);
    }

    public String color(String name, String step) {
        String token = name + "-" + step;
        String hex = PALETTE.get(token);
        if (hex == null) {
            throw new IllegalArgumentException("未知色彩令牌: " + token);
        }
        return hex;
    }

    public boolean hasColor(String token) {
        return PALETTE.containsKey(token);
    }

    /** CSS 变量导出（--color-<name>-<step>: hex; 按名排序确定），暗色覆盖优先生效 */
    public List<String> themeVariables(boolean dark) {
        TreeMap<String, String> out = new TreeMap<>();
        for (Map.Entry<String, String> e : PALETTE.entrySet()) {
            out.put("--color-" + e.getKey(), e.getValue());
        }
        if (dark) {
            for (Map.Entry<String, String> e : darkOverrides.entrySet()) {
                String var = e.getKey().startsWith("--") ? e.getKey() : "--color-" + e.getKey();
                out.put(var, e.getValue());
            }
        }
        List<String> lines = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : out.entrySet()) {
            lines.add(e.getKey() + ": " + e.getValue() + ";");
        }
        return lines;
    }
}
