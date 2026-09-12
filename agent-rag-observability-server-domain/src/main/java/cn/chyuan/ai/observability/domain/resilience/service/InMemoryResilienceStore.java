package cn.chyuan.ai.observability.domain.resilience.service;

import cn.chyuan.ai.observability.domain.resilience.adapter.port.IResilienceStore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调度韧性内存实现（工单 0220-0223 AD 簇；单机/测试默认，PG/MySQL 经 MyBatis 端口实现）
 *
 * @author chyuan
 */
public class InMemoryResilienceStore implements IResilienceStore {

    private final Map<String, LagSnapshot> lags = new ConcurrentHashMap<>();
    private final Map<String, QualityRule> rules = new ConcurrentHashMap<>();
    private final Map<String, QualityRunner.QualityResult> results = new ConcurrentHashMap<>();
    private final Map<String, BackfillJob> backfills = new ConcurrentHashMap<>();
    private final Map<String, SlaMiss> slaMisses = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong();

    private long next() {
        return seq.incrementAndGet();
    }

    @Override
    public void saveLag(LagSnapshot snapshot) {
        lags.put(snapshot.topic() + "#" + snapshot.sampledAt() + "#" + next(), snapshot);
    }

    @Override
    public List<LagSnapshot> recentLags(int limit) {
        return lags.values().stream()
                .sorted(Comparator.comparingLong(LagSnapshot::sampledAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public void upsertRule(QualityRule rule) {
        rules.put(rule.name(), rule);
    }

    @Override
    public void deleteRule(String ruleName) {
        rules.remove(ruleName);
    }

    @Override
    public QualityRule findRule(String ruleName) {
        return rules.get(ruleName);
    }

    @Override
    public List<QualityRule> listRules() {
        return List.copyOf(rules.values());
    }

    @Override
    public void saveResult(QualityRunner.QualityResult result) {
        results.put(result.ruleName() + "#" + result.ranAt() + "#" + next(), result);
    }

    @Override
    public List<QualityRunner.QualityResult> recentResults(int limit) {
        return results.values().stream()
                .sorted(Comparator.comparingLong(QualityRunner.QualityResult::ranAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public void upsertBackfill(BackfillJob job) {
        backfills.put(job.id(), job);
    }

    @Override
    public BackfillJob findBackfill(String id) {
        return backfills.get(id);
    }

    @Override
    public void saveSlaMiss(SlaMiss miss) {
        slaMisses.put(miss.task() + "#" + miss.detectedAt() + "#" + next(), miss);
    }

    @Override
    public List<SlaMiss> recentSlaMisses(int limit) {
        return slaMisses.values().stream()
                .sorted(Comparator.comparingLong(SlaMiss::detectedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }
}
