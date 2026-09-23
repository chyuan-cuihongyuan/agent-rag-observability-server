package cn.chyuan.ai.observability.domain.regexkernel.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 捕获组与替换（工单 0638 BX5，ripgrep/RE2 捕获面思想）。
 * 编号组边界/命名组（(?P<name>…)）别名索引/未参与匹配组语义（null）/
 * 替换模板 $1·${name} 展开（$$ 转义）/未命名组引用拒绝。
 */
public final class CaptureGroups {

    /** 命名索引（名→组号） */
    private final Map<String, Integer> names;

    public CaptureGroups(Map<String, Integer> names) {
        this.names = new LinkedHashMap<>(names);
    }

    /** 从模式解析命名索引 */
    public static CaptureGroups fromPattern(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("模式不得为 null");
        }
        Map<String, Integer> names = new LinkedHashMap<>();
        int groupIndex = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\' ) {
                i++;
            } else if (c == '[') {
                i = skipClass(pattern, i);
            } else if (c == '(') {
                if (pattern.startsWith("?:", i + 1)) {
                    i += 2;
                    continue;
                }
                groupIndex++;
                if (pattern.startsWith("?P<", i + 1)) {
                    int close = pattern.indexOf('>', i + 4);
                    if (close < 0) {
                        throw new IllegalArgumentException("命名组未闭合");
                    }
                    names.put(pattern.substring(i + 4, close), groupIndex);
                    i = close;
                }
            }
        }
        return new CaptureGroups(names);
    }

    private static int skipClass(String pattern, int open) {
        int i = open + 1;
        if (i < pattern.length() && pattern.charAt(i) == '^') {
            i++;
        }
        if (i < pattern.length() && pattern.charAt(i) == ']') {
            i++;
        }
        while (i < pattern.length() && pattern.charAt(i) != ']') {
            if (pattern.charAt(i) == '\\') {
                i++;
            }
            i++;
        }
        return Math.min(i, pattern.length() - 1);
    }

    public Integer groupOf(String name) {
        return names.get(name);
    }

    public Map<String, Integer> names() {
        return new LinkedHashMap<>(names);
    }

    /** 替换模板展开：$1/$2…/$name/${name}；$$ 转义字面 $ */
    public String expand(String template, String input, PikeVm.Match match) {
        if (template == null || match == null) {
            throw new IllegalArgumentException("模板与匹配不得为 null");
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c != '$') {
                out.append(c);
                i++;
                continue;
            }
            if (i + 1 < template.length() && template.charAt(i + 1) == '$') {
                out.append('$');
                i += 2;
                continue;
            }
            if (i + 1 < template.length() && template.charAt(i + 1) == '{') {
                int close = template.indexOf('}', i + 2);
                if (close < 0) {
                    throw new IllegalArgumentException("替换模板 ${ 未闭合");
                }
                String name = template.substring(i + 2, close);
                out.append(namedGroup(name, input, match));
                i = close + 1;
                continue;
            }
            int j = i + 1;
            while (j < template.length() && Character.isDigit(template.charAt(j))) {
                j++;
            }
            if (j == i + 1) {
                out.append('$');
                i++;
                continue;
            }
            int group = Integer.parseInt(template.substring(i + 1, j));
            String value = match.group(group, input);
            if (value == null) {
                throw new IllegalArgumentException("未参与匹配的组引用：$" + group);
            }
            out.append(value);
            i = j;
        }
        return out.toString();
    }

    private String namedGroup(String name, String input, PikeVm.Match match) {
        Integer group = names.get(name);
        if (group == null) {
            throw new IllegalArgumentException("未知命名组：${" + name + "}");
        }
        String value = match.group(group, input);
        if (value == null) {
            throw new IllegalArgumentException("未参与匹配的组引用：${" + name + "}");
        }
        return value;
    }
}
