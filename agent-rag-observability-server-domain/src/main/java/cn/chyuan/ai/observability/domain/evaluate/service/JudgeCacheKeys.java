package cn.chyuan.ai.observability.domain.evaluate.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * judge 缓存键纯函数（工单 0176 X7）— 归一化（去首尾空白）后 sha256；
 * 维度 prompt 已含 rubric 版本语义（prompt 渲染自 rubric 版本内容），键即隐含版本。
 */
public final class JudgeCacheKeys {

    private JudgeCacheKeys() {}

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 不可用", e);
        }
    }

    /** 归一化 + 哈希：去首尾空白后散列（判定的等价输入语义以逐字一致为准，不做更深归一） */
    public static String cacheKey(String prompt) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        return sha256(prompt.trim());
    }
}
