package cn.chyuan.ai.observability.domain.designkernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 类名解析与规则生成（工单 0879-0881/0884 CZ2·CZ3·CZ4·CZ7，tailwind 思想）。
 * 前缀属性值三段解析与任意值语法/变体栈前缀拼接/JIT 规则生成未映射拒绝/组合类冲突后者胜排序确定。
 */
public final class ClassGenerator {

    /** 属性映射：前缀 → CSS 属性 */
    public static final Map<String, String> PROPERTIES = Map.of(
            "bg", "background-color",
            "text", "color",
            "p", "padding",
            "px", "padding-left",
            "m", "margin",
            "w", "width",
            "h", "height");

    /** 生成的一条规则 */
    public record Rule(String className, String selector, String property, String value) {
    }

    /** 解析结果：变体栈 + 属性前缀 + 值（任意值标记） */
    public record Parsed(List<String> variants, String property, String rawValue, boolean arbitrary) {
    }

    private final DesignTokens tokens;
    private final boolean themeVars;

    public ClassGenerator(DesignTokens tokens) {
        this(tokens, false);
    }

    public ClassGenerator(DesignTokens tokens, boolean themeVars) {
        this.tokens = tokens;
        this.themeVars = themeVars;
    }

    /** 解析：变体栈 hover·focus·md 等 / 属性 / 值（[...] 任意值） */
    public Parsed parse(String className) {
        List<String> variants = new ArrayList<>();
        String rest = className;
        int idx;
        while ((idx = rest.indexOf(':')) >= 0) {
            variants.add(rest.substring(0, idx));
            rest = rest.substring(idx + 1);
        }
        int dash = rest.indexOf('-');
        if (dash <= 0) {
            throw new IllegalArgumentException("类名缺少属性分隔: " + className);
        }
        String property = rest.substring(0, dash);
        if (!PROPERTIES.containsKey(property)) {
            throw new IllegalArgumentException("未知属性前缀: " + property);
        }
        String value = rest.substring(dash + 1);
        boolean arbitrary = value.startsWith("[") && value.endsWith("]");
        return new Parsed(List.copyOf(variants), property, value, arbitrary);
    }

    /** JIT 生成：尺度映射或任意值；未映射拒绝 */
    public Rule generate(String className) {
        Parsed parsed = parse(className);
        String cssProperty = PROPERTIES.get(parsed.property());
        String value = valueOf(parsed, className);
        String escaped = className.replace("[", "\\[").replace("]", "\\]").replace(":", "\\:");
        String selector = "." + escaped;
        for (String variant : parsed.variants()) {
            if (variant.equals("hover")) {
                selector += ":hover";
            } else if (variant.equals("focus")) {
                selector += ":focus";
            } else if (variant.equals("active")) {
                selector += ":active";
            } else if (variant.equals("md")) {
                selector += "@md";
            }
        }
        return new Rule(className, selector, cssProperty, value);
    }

    private String valueOf(Parsed parsed, String className) {
        String raw = parsed.rawValue();
        if (parsed.arbitrary()) {
            return raw.substring(1, raw.length() - 1);
        }
        if (parsed.property().equals("bg") || parsed.property().equals("text")) {
            int stepDash = raw.lastIndexOf('-');
            if (stepDash < 0) {
                throw new IllegalArgumentException("色彩缺少阶: " + className);
            }
            String name = raw.substring(0, stepDash);
            String step = raw.substring(stepDash + 1);
            String hex = tokens.color(name, step);
            return themeVars ? "var(--color-" + name + "-" + step + ")" : hex;
        }
        if (raw.matches("\\d+(\\.5)?")) {
            return tokens.spacingPx(raw) + "px";
        }
        throw new IllegalArgumentException("未映射值: " + className);
    }

    /** 组合类：同选择器同属性后者胜（原位替换保序），异属性共存，顺序确定 */
    public List<Rule> combine(List<String> classNames) {
        Map<String, Rule> index = new java.util.LinkedHashMap<>();
        List<Rule> order = new ArrayList<>();
        for (String className : classNames) {
            Rule rule = generate(className);
            String key = rule.selector() + "|" + rule.property();
            Rule prev = index.get(key);
            if (prev != null) {
                order.set(order.indexOf(prev), rule);
            } else {
                order.add(rule);
            }
            index.put(key, rule);
        }
        return List.copyOf(order);
    }
}
