package cn.chyuan.ai.observability.domain.logkernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * LogQL 子集流选择器（工单 0574 BQ2，loki stream selector 思想）。
 * {label=value} 等值与 {label=~"regex"} 全匹配正则/多标签选择器交集/
 * 未匹配流裁剪/非法正则拒绝。纯函数：标签集进，命中判定出。
 */
public final class StreamSelector {

    /** 单匹配器 */
    public record Matcher(String label, String value, boolean regex) {
    }

    private final List<Matcher> matchers = new ArrayList<>();

    /** 等值匹配器 */
    public StreamSelector equal(String label, String value) {
        requireLabel(label);
        if (value == null) {
            throw new IllegalArgumentException("匹配值不得为 null");
        }
        matchers.add(new Matcher(label, value, false));
        return this;
    }

    /** 正则匹配器（Loki =~ 全匹配语义；非法正则拒绝） */
    public StreamSelector regex(String label, String pattern) {
        requireLabel(label);
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("非法正则: " + pattern, e);
        }
        matchers.add(new Matcher(label, pattern, true));
        return this;
    }

    public List<Matcher> matchers() {
        return List.copyOf(matchers);
    }

    /** 标签集是否命中全部匹配器（交集语义；缺标签不命中） */
    public boolean matches(Map<String, String> labels) {
        if (labels == null) {
            return false;
        }
        for (Matcher matcher : matchers) {
            String value = labels.get(matcher.label());
            if (value == null) {
                return false;
            }
            if (matcher.regex()) {
                if (!Pattern.compile(matcher.value()).matcher(value).matches()) {
                    return false;
                }
            } else if (!value.equals(matcher.value())) {
                return false;
            }
        }
        return true;
    }

    /** 流集合裁剪：流键 → 标签集，仅保留命中流（保持输入序） */
    public List<String> select(Map<String, Map<String, String>> streams) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : streams.entrySet()) {
            if (matches(entry.getValue())) {
                out.add(entry.getKey());
            }
        }
        return List.copyOf(out);
    }

    private void requireLabel(String label) {
        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException("标签名不得为空");
        }
    }
}
