package cn.chyuan.ai.observability.domain.barrierkernel.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 快照 ack（工单 1047 EN3，flink acknowledge 思想）。
 * 算子 ack 收齐完成/缺 ack 未完成/重复 ack 幂等。
 */
public final class CheckpointAcks {

    private final Map<Long, Set<String>> pending = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> expected = new ConcurrentHashMap<>();

    /** 开启 ack 窗口：登记期望算子集 */
    public synchronized void start(long checkpointId, List<String> operators) {
        if (operators == null || operators.isEmpty()) {
            throw new IllegalArgumentException("期望算子为空");
        }
        expected.put(checkpointId, new HashSet<>(operators));
        pending.put(checkpointId, new HashSet<>());
    }

    /** 算子 ack：未注册算子拒绝；重复 ack 幂等；收齐返回 true */
    public synchronized boolean ack(long checkpointId, String operator) {
        Set<String> expect = expected.get(checkpointId);
        if (expect == null) {
            throw new IllegalArgumentException("checkpoint 未开启 ack: " + checkpointId);
        }
        if (!expect.contains(operator)) {
            throw new IllegalArgumentException("未注册算子: " + operator);
        }
        pending.get(checkpointId).add(operator);
        return pending.get(checkpointId).containsAll(expect);
    }

    public synchronized boolean isComplete(long checkpointId) {
        Set<String> expect = expected.get(checkpointId);
        Set<String> got = pending.get(checkpointId);
        return expect != null && got != null && got.containsAll(expect);
    }

    /** 完成收尾：清理窗口 */
    public synchronized void finish(long checkpointId) {
        if (!isComplete(checkpointId)) {
            throw new IllegalStateException("未收齐不可 finish: " + checkpointId);
        }
        expected.remove(checkpointId);
        pending.remove(checkpointId);
    }

    public synchronized void abort(long checkpointId) {
        expected.remove(checkpointId);
        pending.remove(checkpointId);
    }

    public synchronized int pendingCount() {
        return expected.size();
    }
}
