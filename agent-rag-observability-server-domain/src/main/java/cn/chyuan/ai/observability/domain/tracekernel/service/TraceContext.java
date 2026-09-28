package cn.chyuan.ai.observability.domain.tracekernel.service;

import java.util.Random;

/**
 * traceparent 上下文（工单 0968 EE1，W3C TraceContext / jaeger 思想）。
 * 解析四段（版本-追踪 id-跨度 id-标志）/非法拒绝/生成新上下文。
 */
public final class TraceContext {

    public final String version;
    public final String traceId;
    public final String spanId;
    public final int flags;

    private TraceContext(String version, String traceId, String spanId, int flags) {
        this.version = version;
        this.traceId = traceId;
        this.spanId = spanId;
        this.flags = flags;
    }

    /** 解析 traceparent 头：四段定长 hex、版本非 ff、追踪 id 非全零 */
    public static TraceContext parse(String traceparent) {
        if (traceparent == null) {
            throw new IllegalArgumentException("traceparent 为空");
        }
        String[] parts = traceparent.split("-");
        if (parts.length != 4) {
            throw new IllegalArgumentException("段数非法: " + traceparent);
        }
        String version = parts[0];
        String traceId = parts[1];
        String spanId = parts[2];
        String flags = parts[3];
        if (!isHex(version, 2) || "ff".equals(version)) {
            throw new IllegalArgumentException("版本非法: " + version);
        }
        if (!isHex(traceId, 32) || traceId.chars().allMatch(c -> c == '0')) {
            throw new IllegalArgumentException("追踪 id 非法: " + traceId);
        }
        if (!isHex(spanId, 16) || spanId.chars().allMatch(c -> c == '0')) {
            throw new IllegalArgumentException("跨度 id 非法: " + spanId);
        }
        if (!isHex(flags, 2)) {
            throw new IllegalArgumentException("标志非法: " + flags);
        }
        return new TraceContext(version, traceId, spanId, Integer.parseInt(flags, 16));
    }

    /** 生成新上下文：版本 00、标志 01（采样） */
    public static TraceContext generate(Random random) {
        return new TraceContext("00", hex(random, 16), hex(random, 8), 0x01);
    }

    /** 序列化回 traceparent 头 */
    public String traceparent() {
        return version + "-" + traceId + "-" + spanId + "-" + String.format("%02x", flags);
    }

    /** 采样位（flags 最低位） */
    public boolean sampled() {
        return (flags & 0x01) == 0x01;
    }

    private static boolean isHex(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        return value.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
    }

    private static String hex(Random random, int bytes) {
        byte[] raw = new byte[bytes];
        random.nextBytes(raw);
        StringBuilder out = new StringBuilder();
        for (byte b : raw) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }
}
