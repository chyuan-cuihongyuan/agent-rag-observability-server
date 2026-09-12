package cn.chyuan.ai.observability.domain.resilience.service;

import java.util.TreeSet;

/**
 * 回填任务（工单 0222 AD3）：范围 + 分片数 + 已完成分片集 + 状态。
 * 幂等语义由 BackfillExecutor 保证：仅执行 pending 分片，完成即记档，重跑跳过已完成。
 */
public record BackfillJob(String id, String name, long rangeStart, long rangeEnd, int shardCount,
        TreeSet<Integer> completedShards, String status, long createdAt, long updatedAt) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_DONE = "DONE";

    public BackfillJob {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("回填任务 id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("回填任务名不能为空");
        }
        completedShards = completedShards == null ? new TreeSet<>() : new TreeSet<>(completedShards);
    }

    public boolean done() {
        return completedShards.size() >= shardCount;
    }

    public BackfillJob markRunning(long now) {
        return new BackfillJob(id, name, rangeStart, rangeEnd, shardCount, completedShards,
                STATUS_RUNNING, createdAt, now);
    }

    public BackfillJob withCompleted(int shardIndex, long now) {
        TreeSet<Integer> next = new TreeSet<>(completedShards);
        next.add(shardIndex);
        return new BackfillJob(id, name, rangeStart, rangeEnd, shardCount, next,
                next.size() >= shardCount ? STATUS_DONE : STATUS_RUNNING, createdAt, now);
    }
}
