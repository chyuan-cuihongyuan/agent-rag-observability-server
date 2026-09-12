package cn.chyuan.ai.observability.domain.resilience.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调度韧性单测（工单 0220-0223 AD 簇）：水位分级/质量断言/回填幂等/SLA 判定。
 */
class ResilienceServiceTest {

    private static ResilienceService service() {
        return new ResilienceService(new InMemoryResilienceStore(), 1000, 10_000);
    }

    // ── AD1 ──

    @Test
    void 延迟采样分级落档() {
        ResilienceService service = service();
        Map<String, Object> round = service.sampleLag(() -> Map.of("t-ok", 10L, "t-warn", 2000L,
                "t-crit", 99_999L), 123L);
        assertEquals("CRITICAL", round.get("worst"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) round.get("samples");
        assertEquals(3, samples.size());
        assertEquals(3, service.recentLags(10).size());
    }

    @Test
    void 水位分级边界() {
        assertEquals(LagWatermark.LEVEL_OK, LagWatermark.grade(0, 1000, 10_000));
        assertEquals(LagWatermark.LEVEL_WARN, LagWatermark.grade(1000, 1000, 10_000));
        assertEquals(LagWatermark.LEVEL_OK, LagWatermark.grade(999, 1000, 10_000));
        assertEquals(LagWatermark.LEVEL_CRITICAL, LagWatermark.grade(10_000, 1000, 10_000));
        // 负 lag 钳 0；脏阈值回退默认
        assertEquals(LagWatermark.LEVEL_OK, LagWatermark.grade(-5, -1, 0));
        assertEquals(LagWatermark.LEVEL_WARN, LagWatermark.grade(1000, -1, 0));
        // 批量最差水位
        assertEquals(LagWatermark.LEVEL_WARN, LagWatermark.worstLevel(Map.of("a", "OK", "b", "WARN")));
        assertEquals(LagWatermark.LEVEL_OK, LagWatermark.worstLevel(Map.of()));
    }

    // ── AD2 ──

    @Test
    void 质量断言四类规则() {
        ResilienceService service = service();
        service.upsertRule(new QualityRule(null, "r-notnull", "orders", "user_id",
                QualityRule.TYPE_NOT_NULL, Map.of(), true));
        service.upsertRule(new QualityRule(null, "r-range", "orders", "amount",
                QualityRule.TYPE_RANGE, Map.of("min", "0", "max", "100"), true));
        service.upsertRule(new QualityRule(null, "r-enum", "orders", "status",
                QualityRule.TYPE_ENUM, Map.of("values", "NEW,PAID"), true));
        service.upsertRule(new QualityRule(null, "r-fresh", "orders", "ts",
                QualityRule.TYPE_FRESHNESS, Map.of("maxAgeMs", "1000"), true));
        assertEquals(4, service.listRules().size());

        long now = 1_000_000L;
        List<Map<String, Object>> rows = List.of(
                Map.of("user_id", "u1", "amount", 5, "status", "NEW", "ts", now),
                Map.of("user_id", "", "amount", 500, "status", "BAD", "ts", 1L));
        QualityRunner.QualityResult notNull = service.runQuality("r-notnull", rows, now);
        assertFalse(notNull.pass());
        assertEquals(1, notNull.violated());
        assertFalse(service.runQuality("r-range", rows, now).pass());
        assertFalse(service.runQuality("r-enum", rows, now).pass());
        assertFalse(service.runQuality("r-fresh", rows, now).pass());
        // 全合规行集
        List<Map<String, Object>> good = List.of(Map.of("user_id", "u", "amount", 5,
                "status", "NEW", "ts", now));
        assertTrue(service.runQuality("r-range", good, now).pass());
        // 失败样本封顶 5 + 结果留痕 + 规则不存在
        assertTrue(notNull.failureSamples().size() <= 5);
        assertEquals(5, service.recentResults(10).size());
        assertThrows(IllegalArgumentException.class, () -> service.runQuality("ghost", rows, now));
        assertTrue(service.deleteRule("r-enum"));
        assertFalse(service.deleteRule("r-enum"));
    }

    // ── AD3 ──

    @Test
    void 回填规划与幂等执行() {
        ResilienceService service = service();
        // 规划：10 个键空间 3 分片 → [0,3][4,6][7,9]
        List<BackfillPlanner.Shard> shards = BackfillPlanner.plan(0, 9, 3);
        assertEquals(3, shards.size());
        assertEquals(0, shards.get(0).start());
        assertEquals(3, shards.get(0).end());
        assertEquals(9, shards.get(2).end());
        // 非法范围/分片数
        assertThrows(IllegalArgumentException.class, () -> BackfillPlanner.plan(10, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> BackfillPlanner.plan(0, 9, 0));

        BackfillJob job = service.createBackfill("补数-订单", 0, 9, 3, 100L);
        AtomicInteger executed = new AtomicInteger();
        BackfillJob done = service.runBackfill(job.id(), shard -> executed.incrementAndGet(), 200L);
        assertEquals(BackfillJob.STATUS_DONE, done.status());
        assertEquals(3, executed.get());
        // 重跑幂等：已完成分片跳过
        BackfillJob rerun = service.runBackfill(job.id(), shard -> executed.incrementAndGet(), 300L);
        assertEquals(BackfillJob.STATUS_DONE, rerun.status());
        assertEquals(3, executed.get(), "重跑不应重复执行分片");
        // 分片失败中断：已完成保留续跑
        BackfillJob job2 = service.createBackfill("补数-失败", 0, 2, 3, 100L);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> service.runBackfill(job2.id(), shard -> {
            if (calls.incrementAndGet() == 2) {
                throw new IllegalStateException("分片失败");
            }
        }, 200L));
        assertEquals(1, service.findBackfill(job2.id()).completedShards().size());
    }

    // ── AD4 ──

    @Test
    void sla判定与留痕() {
        ResilienceService service = service();
        assertNull(service.judgeSla("巡检", 60_000, 50_000, 1L), "未超时无 miss");
        SlaMiss miss = service.judgeSla("巡检", 60_000, 90_000, 2L);
        assertNotNull(miss);
        assertEquals(30_000, miss.overdueMs());
        service.judgeSla("挖掘", 1000, 5000, 3L);
        assertEquals(2, service.recentSlaMisses(10).size());
        assertEquals("挖掘", service.recentSlaMisses(10).get(0).task(), "按时间倒序");
        // 预期时长负值钳 0
        assertNotNull(SlaMiss.judgeIfMissed("t", -1, 1, 0L));
    }
}
