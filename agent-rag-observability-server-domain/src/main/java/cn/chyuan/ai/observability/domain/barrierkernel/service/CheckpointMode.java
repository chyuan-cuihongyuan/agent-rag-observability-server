package cn.chyuan.ai.observability.domain.barrierkernel.service;

import java.util.HashMap;
import java.util.Map;

/**
 * 模式语义（工单 1051 EN7，flink exactly-once/at-least-once 思想）。
 * exactly-once 对齐/at-least-once 首通道直通/直通记录去重标记。
 */
public final class CheckpointMode {

    public enum Mode {
        EXACTLY_ONCE, AT_LEAST_ONCE
    }

    private final Mode mode;
    private final Map<String, Boolean> delivered = new HashMap<>();

    public CheckpointMode(Mode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("模式为空");
        }
        this.mode = mode;
    }

    public Mode mode() {
        return mode;
    }

    /** exactly-once 需对齐（数据遇 barrier 入缓冲）；at-least-once 直通不缓冲 */
    public boolean requiresAlignment() {
        return mode == Mode.EXACTLY_ONCE;
    }

    /** 直通投递：首次标记去重键；重复投递返回 dup 标记 */
    public synchronized boolean passThrough(String dedupKey) {
        if (requiresAlignment()) {
            throw new IllegalStateException("exactly-once 不走直通");
        }
        Boolean seen = delivered.put(dedupKey, Boolean.TRUE);
        return seen != null;
    }

    /** 清空去重标记（新 checkpoint 窗口） */
    public synchronized void resetMarks() {
        delivered.clear();
    }

    public synchronized int marks() {
        return delivered.size();
    }
}
