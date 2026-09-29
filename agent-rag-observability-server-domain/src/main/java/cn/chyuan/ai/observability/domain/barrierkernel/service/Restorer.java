package cn.chyuan.ai.observability.domain.barrierkernel.service;

import java.util.Map;
import java.util.TreeMap;

/**
 * 恢复（工单 1050 EN6，flink restore 思想）。
 * 最近完成快照恢复/无快照拒绝/恢复后 checkpointId 递增。
 */
public final class Restorer {

    private final TreeMap<Long, Long> completed = new TreeMap<>();
    private long highestId;

    /** 登记完成快照（checkpointId 单调） */
    public synchronized void register(long checkpointId) {
        if (checkpointId <= highestId && !completed.isEmpty()) {
            throw new IllegalStateException("checkpointId 非递增: " + checkpointId);
        }
        highestId = Math.max(highestId, checkpointId);
        completed.put(checkpointId, checkpointId);
    }

    /** 最近完成快照 id；无快照拒绝 */
    public synchronized long latest() {
        Map.Entry<Long, Long> last = completed.lastEntry();
        if (last == null) {
            throw new IllegalStateException("无完成快照可恢复");
        }
        return last.getKey();
    }

    /** 恢复后下一 checkpointId = 最近完成 + 1 */
    public synchronized long nextCheckpointId() {
        return latest() + 1;
    }

    public synchronized boolean hasSnapshot() {
        return !completed.isEmpty();
    }

    public synchronized int count() {
        return completed.size();
    }
}
