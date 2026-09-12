package cn.chyuan.ai.observability.domain.resilience.service;

import cn.chyuan.ai.observability.domain.resilience.adapter.port.IResilienceStore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 调度韧性服务（工单 0220-0223 AD 簇总装）—
 * AD1 消费延迟：采样（IMqLagPort）→ 水位分级 → 落档，超 WARN 级即视为分级事件；
 * AD2 质量断言：规则注册 + 行集求值 + 结果留痕（失败不中断后续规则）；
 * AD3 回填：规划分片 + 幂等执行（仅 pending 分片，逐片落档，重跑跳过已完成）；
 * AD4 SLA：判定超时即留痕（SlaMiss.judgeIfMissed）。
 *
 * @author chyuan
 */
public class ResilienceService {

    /** MQ 延迟端口（生产适配 MQ 管理面；测试 mock） */
    public interface MqLagPort {
        Map<String, Long> lagByTopic();
    }

    /** 回填分片执行函数（真实数据面接入点；测试用桩） */
    @FunctionalInterface
    public interface ShardFn extends Consumer<BackfillPlanner.Shard> {
    }

    private final IResilienceStore store;
    private final long lagWarnThreshold;
    private final long lagCriticalThreshold;

    public ResilienceService(IResilienceStore store, long lagWarnThreshold, long lagCriticalThreshold) {
        this.store = store;
        this.lagWarnThreshold = lagWarnThreshold > 0 ? lagWarnThreshold : LagWatermark.DEFAULT_WARN;
        this.lagCriticalThreshold = lagCriticalThreshold > this.lagWarnThreshold
                ? lagCriticalThreshold : this.lagWarnThreshold * 10;
    }

    // ── AD1：延迟采样 ──

    /** 采样一轮：全 topic 分级落档；返回本轮快照（含最差水位） */
    public Map<String, Object> sampleLag(MqLagPort port, long nowMs) {
        Map<String, Long> lagByTopic = port.lagByTopic();
        Map<String, String> levels = LagWatermark.gradeAll(lagByTopic, lagWarnThreshold, lagCriticalThreshold);
        List<LagSnapshot> round = new java.util.ArrayList<>();
        lagByTopic.forEach((topic, lag) -> {
            LagSnapshot snapshot = new LagSnapshot(topic, lag == null ? 0 : lag,
                    levels.get(topic), nowMs);
            store.saveLag(snapshot);
            round.add(snapshot);
        });
        return Map.of("samples", round.stream().map(LagSnapshot::toMap).toList(),
                "worst", LagWatermark.worstLevel(levels));
    }

    public List<LagSnapshot> recentLags(int limit) {
        return store.recentLags(limit);
    }

    // ── AD2：质量断言 ──

    public QualityRule upsertRule(QualityRule rule) {
        store.upsertRule(rule);
        return rule;
    }

    public List<QualityRule> listRules() {
        return store.listRules();
    }

    public boolean deleteRule(String ruleName) {
        if (store.findRule(ruleName) == null) {
            return false;
        }
        store.deleteRule(ruleName);
        return true;
    }

    /** 求值单规则并留痕；规则不存在抛 IllegalArgumentException */
    public QualityRunner.QualityResult runQuality(String ruleName, List<Map<String, Object>> rows, long nowMs) {
        QualityRule rule = store.findRule(ruleName);
        if (rule == null) {
            throw new IllegalArgumentException("质量规则不存在: " + ruleName);
        }
        QualityRunner.QualityResult result = QualityRunner.evaluate(rule, rows, nowMs);
        store.saveResult(result);
        return result;
    }

    public List<QualityRunner.QualityResult> recentResults(int limit) {
        return store.recentResults(limit);
    }

    // ── AD3：回填 ──

    /** 创建回填任务（规划分片入档，状态 PENDING） */
    public BackfillJob createBackfill(String name, long rangeStart, long rangeEnd, int shardCount, long nowMs) {
        List<BackfillPlanner.Shard> shards = BackfillPlanner.plan(rangeStart, rangeEnd, shardCount);
        BackfillJob job = new BackfillJob(UUID.randomUUID().toString(), name, rangeStart, rangeEnd,
                shards.size(), new TreeSet<>(), BackfillJob.STATUS_PENDING, nowMs, nowMs);
        store.upsertBackfill(job);
        return job;
    }

    /**
     * 幂等执行：仅 pending 分片，每片成功即落档；重跑跳过已完成分片（不重不漏）。
     * 分片函数抛错即中断（已完成分片保留，下次续跑）。
     */
    public BackfillJob runBackfill(String id, ShardFn shardFn, long nowMs) {
        BackfillJob job = store.findBackfill(id);
        if (job == null) {
            throw new IllegalArgumentException("回填任务不存在: " + id);
        }
        // 已完成任务直接返回（重跑不回置 RUNNING）
        if (job.done()) {
            return job;
        }
        List<BackfillPlanner.Shard> all = BackfillPlanner.plan(job.rangeStart(), job.rangeEnd(),
                job.shardCount());
        BackfillJob current = job.markRunning(nowMs);
        store.upsertBackfill(current);
        for (BackfillPlanner.Shard shard : BackfillPlanner.pending(all, current.completedShards())) {
            shardFn.accept(shard);
            current = current.withCompleted(shard.index(), System.currentTimeMillis());
            store.upsertBackfill(current);
        }
        return current;
    }

    public BackfillJob findBackfill(String id) {
        return store.findBackfill(id);
    }

    // ── AD4：SLA ──

    /** 判定：actual > expected 记 SLA miss 留痕；返回 miss（未超时返回 null） */
    public SlaMiss judgeSla(String task, long expectedMs, long actualMs, long nowMs) {
        SlaMiss miss = SlaMiss.judgeIfMissed(task, expectedMs, actualMs, nowMs);
        if (miss != null) {
            store.saveSlaMiss(miss);
        }
        return miss;
    }

    public List<SlaMiss> recentSlaMisses(int limit) {
        List<SlaMiss> misses = store.recentSlaMisses(limit);
        misses = new java.util.ArrayList<>(misses);
        misses.sort(Comparator.comparingLong(SlaMiss::detectedAt).reversed());
        return misses;
    }
}
