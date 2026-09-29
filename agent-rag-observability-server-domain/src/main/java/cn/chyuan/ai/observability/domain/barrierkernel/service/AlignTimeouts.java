package cn.chyuan.ai.observability.domain.barrierkernel.service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对齐超时（工单 1048 EN4，flink timeout 思想）。
 * 超时判定失败/未超时继续等待/失败释放缓冲。
 */
public final class AlignTimeouts {

    /** 对齐会话状态 */
    public enum State {
        WAITING, FAILED
    }

    private final Map<Long, Long> deadlines = new HashMap<>();
    private final Map<Long, State> states = new HashMap<>();

    /** 开启对齐会话：deadline = now + timeout */
    public synchronized void start(long checkpointId, long now, long timeoutTicks) {
        if (timeoutTicks <= 0) {
            throw new IllegalArgumentException("超时须为正: " + timeoutTicks);
        }
        deadlines.put(checkpointId, now + timeoutTicks);
        states.put(checkpointId, State.WAITING);
    }

    /** 未超时继续等待（返回 false 不打断）；超时判失败并落定 FAILED */
    public synchronized boolean expired(long checkpointId, long now) {
        State state = states.get(checkpointId);
        if (state == null) {
            throw new IllegalArgumentException("对齐会话不存在: " + checkpointId);
        }
        if (state == State.FAILED) {
            return true;
        }
        if (now >= deadlines.get(checkpointId)) {
            states.put(checkpointId, State.FAILED);
            return true;
        }
        return false;
    }

    public synchronized State state(long checkpointId) {
        State state = states.get(checkpointId);
        if (state == null) {
            throw new IllegalArgumentException("对齐会话不存在: " + checkpointId);
        }
        return state;
    }

    /** 会话收尾清理 */
    public synchronized void clear(long checkpointId) {
        deadlines.remove(checkpointId);
        states.remove(checkpointId);
    }

    public synchronized int sessions() {
        return states.size();
    }
}
