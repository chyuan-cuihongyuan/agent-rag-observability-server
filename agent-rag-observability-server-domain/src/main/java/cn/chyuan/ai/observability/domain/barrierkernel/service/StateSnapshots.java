package cn.chyuan.ai.observability.domain.barrierkernel.service;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态快照（工单 1049 EN5，flink state snapshot 思想）。
 * 算子状态版本化/两次快照隔离互不覆盖/快照只读。
 */
public final class StateSnapshots {

    private final Map<String, Map<String, Object>> current = new HashMap<>();
    private final Map<Long, Map<String, Map<String, Object>>> versions = new HashMap<>();

    /** 写算子状态 */
    public synchronized void put(String operator, String key, Object value) {
        current.computeIfAbsent(operator, k -> new HashMap<>()).put(key, value);
    }

    public synchronized Object get(String operator, String key) {
        Map<String, Object> state = current.get(operator);
        return state == null ? null : state.get(key);
    }

    /** 快照：当前全部算子状态深拷贝版本化；重复 checkpointId 拒绝 */
    public synchronized void snapshot(long checkpointId) {
        if (versions.containsKey(checkpointId)) {
            throw new IllegalStateException("快照版本重复: " + checkpointId);
        }
        Map<String, Map<String, Object>> copy = new HashMap<>();
        current.forEach((operator, state) -> copy.put(operator, new HashMap<>(state)));
        versions.put(checkpointId, copy);
    }

    /** 版本只读视图；未知版本拒绝 */
    public synchronized Map<String, Map<String, Object>> view(long checkpointId) {
        Map<String, Map<String, Object>> version = versions.get(checkpointId);
        if (version == null) {
            throw new IllegalArgumentException("快照版本不存在: " + checkpointId);
        }
        Map<String, Map<String, Object>> readonly = new HashMap<>();
        version.forEach((operator, state) -> readonly.put(operator, Map.copyOf(state)));
        return readonly;
    }

    /** 恢复：版本内容回填为当前状态 */
    public synchronized void restore(long checkpointId) {
        Map<String, Map<String, Object>> version = versions.get(checkpointId);
        if (version == null) {
            throw new IllegalArgumentException("快照版本不存在: " + checkpointId);
        }
        current.clear();
        version.forEach((operator, state) -> current.put(operator, new HashMap<>(state)));
    }

    public synchronized void discard(long checkpointId) {
        versions.remove(checkpointId);
    }

    public synchronized int versionCount() {
        return versions.size();
    }
}
