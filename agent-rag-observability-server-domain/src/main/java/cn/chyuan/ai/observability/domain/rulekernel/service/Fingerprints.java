package cn.chyuan.ai.observability.domain.rulekernel.service;

import java.util.Map;
import java.util.TreeMap;

/**
 * 去重指纹（工单 1043 EM7，prometheus fingerprint 思想）。
 * 同 fingerprint 不重复触发/标签变化生成新指纹/恢复后指纹复用。
 */
public final class Fingerprints {

    private Fingerprints() {
    }

    /** 指纹 = 规则名 + 排序标签对的确定性散列（FNV-1a 64 位十六进制） */
    public static String of(String rule, Map<String, String> labels) {
        if (rule == null || rule.isEmpty()) {
            throw new IllegalArgumentException("规则名为空");
        }
        StringBuilder seed = new StringBuilder(rule);
        if (labels != null) {
            new TreeMap<>(labels).forEach((k, v) -> seed.append('|').append(k).append('=').append(v));
        }
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < seed.length(); i++) {
            hash ^= seed.charAt(i);
            hash *= 0x100000001b3L;
        }
        return String.format("%016x", hash);
    }

    /** 标签集变化 → 指纹变化 */
    public static boolean distinct(String rule, Map<String, String> a, Map<String, String> b) {
        return !of(rule, a).equals(of(rule, b));
    }
}
