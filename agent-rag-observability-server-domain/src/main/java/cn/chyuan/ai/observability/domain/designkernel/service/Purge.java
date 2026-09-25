package cn.chyuan.ai.observability.domain.designkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * purge 清除（工单 0882 CZ5，tailwind 思想）。
 * 扫描用到的类/未用规则清除/动态拼接白名单保留。
 */
public final class Purge {

    private final List<String> allowPrefixes = new ArrayList<>();

    /** 动态拼接白名单：命中前缀的类始终保留 */
    public void allowPrefix(String prefix) {
        allowPrefixes.add(prefix);
    }

    /** 从内容文本中提取用到的类名（按非类名字符切分） */
    public List<String> scanUsed(List<String> contents) {
        List<String> used = new ArrayList<>();
        for (String content : contents) {
            for (String token : content.split("[\"'\\s`<>]+")) {
                if (!token.isEmpty()) {
                    used.add(token);
                }
            }
        }
        return used;
    }

    /** 清除：规则类名未出现在内容且不命中白名单前缀即删 */
    public List<ClassGenerator.Rule> purge(List<ClassGenerator.Rule> rules, List<String> usedClasses) {
        List<ClassGenerator.Rule> kept = new ArrayList<>();
        for (ClassGenerator.Rule rule : rules) {
            if (usedClasses.contains(rule.className()) || allowlisted(rule.className())) {
                kept.add(rule);
            }
        }
        return kept;
    }

    private boolean allowlisted(String className) {
        for (String prefix : allowPrefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
