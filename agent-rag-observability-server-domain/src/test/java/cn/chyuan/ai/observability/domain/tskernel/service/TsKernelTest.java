package cn.chyuan.ai.observability.domain.tskernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时序内核 BF1-BF7 单测（工单 0480-0486）：
 * 压缩往返/时间分块/标签索引/降采样/保留策略/告警状态机/样本登记。
 */
class TsKernelTest {

    @Test
    void BF1_压缩往返等价() {
        List<Long> timestamps = List.of(1000L, 2000L, 3000L, 4000L, 5000L, 6000L);
        List<Double> values = List.of(1.0, 1.0, 2.5, 2.5, 1.5, 3.0);
        DeltaEncoder encoder = new DeltaEncoder();
        DeltaEncoder.Encoded encoded = encoder.encode(timestamps, values);
        DeltaEncoder.DecodeResult decoded = encoder.decode(encoded);
        assertEquals(timestamps, decoded.timestamps(), "时间戳往返等价");
        assertEquals(values, decoded.values(), "值往返等价");
        assertTrue(encoded.compressionRatio() > 1.0, "压缩率 > 1: " + encoded.compressionRatio());
        assertThrows(IllegalArgumentException.class,
                () -> encoder.encode(List.of(2000L, 1000L), List.of(1.0, 2.0)), "时间戳须升序");
    }

    @Test
    void BF2_时间分块与范围裁剪() {
        TimeBlocker blocker = new TimeBlocker(60_000L);
        List<TimeBlocker.Block> blocks = blocker.chunk(List.of(
                new TimeBlocker.Sample(61_000L, 1),
                new TimeBlocker.Sample(30_000L, 2),
                new TimeBlocker.Sample(90_000L, 3)));
        assertEquals(2, blocks.size(), "60s 窗口两块（61s 与 90s 同窗）");
        assertEquals(30_000L, blocks.get(0).minTimestamp());
        assertEquals(60_000L, blocks.get(0).windowEnd(), "窗口边界对齐");
        List<TimeBlocker.Block> pruned = blocker.pruneByRange(blocks, 65_000L, 95_000L);
        assertEquals(1, pruned.size(), "块索引 min/max 裁剪");
        assertEquals(90_000L, pruned.get(0).maxTimestamp());
        assertThrows(IllegalArgumentException.class, () -> blocker.pruneByRange(blocks, 10L, 5L));
    }

    @Test
    void BF3_标签索引与等值交集() {
        LabelIndex index = new LabelIndex();
        long s1 = index.register("cpu_usage", Map.of("host", "h1", "env", "prod"));
        long s1again = index.register("cpu_usage", Map.of("env", "prod", "host", "h1"));
        assertEquals(s1, s1again, "同签名幂等同 id");
        long s2 = index.register("cpu_usage", Map.of("host", "h2", "env", "prod"));
        index.register("cpu_usage", Map.of("host", "h1", "env", "dev"));
        List<LabelIndex.Series> matched = index.select("cpu_usage",
                new LabelIndex.Selector(Map.of("env", "prod")));
        assertEquals(2, matched.size(), "env=prod 交集两条");
        assertEquals(List.of(s1, s2), matched.stream().map(LabelIndex.Series::seriesId).toList(), "id 升序");
        assertTrue(index.select("cpu_usage", new LabelIndex.Selector(Map.of("env", "staging"))).isEmpty());
    }

    @Test
    void BF4_降采样窗口聚合与链式() {
        List<TimeBlocker.Sample> samples = List.of(
                new TimeBlocker.Sample(10_000L, 1),
                new TimeBlocker.Sample(40_000L, 3),
                new TimeBlocker.Sample(70_000L, 5),
                new TimeBlocker.Sample(100_000L, 7));
        Downsampler avg = new Downsampler(60_000L, Downsampler.Aggregator.AVG);
        List<Downsampler.Point> points = avg.downsample(samples);
        assertEquals(2, points.size());
        assertEquals(2.0, points.get(0).value(), 1e-9, "窗口一 avg(1,3)");
        assertEquals(6.0, points.get(1).value(), 1e-9);
        Downsampler counter = new Downsampler(60_000L, Downsampler.Aggregator.COUNT);
        assertEquals(2.0, counter.downsample(samples).get(0).value(), 1e-9);
        Downsampler maxHour = new Downsampler(3_600_000L, Downsampler.Aggregator.MAX);
        List<Downsampler.Point> chained = Downsampler.chain(samples, List.of(avg, maxHour));
        assertEquals(1, chained.size(), "原始→5m→1h 链式收敛到一点");
        assertEquals(6.0, chained.get(0).value(), 1e-9);
    }

    @Test
    void BF5_保留策略过期淘汰与索引清理() {
        long[] now = {1_000_000L};
        RetentionPolicy policy = new RetentionPolicy(300_000L, () -> now[0]);
        TimeBlocker blocker = new TimeBlocker(60_000L);
        List<TimeBlocker.Block> old = blocker.chunk(List.of(new TimeBlocker.Sample(100_000L, 1)));
        List<TimeBlocker.Block> fresh = blocker.chunk(List.of(new TimeBlocker.Sample(900_000L, 2)));
        policy.attach(1L, old);
        policy.attach(1L, fresh);
        policy.attach(2L, old);
        assertEquals(3, policy.blockCount());
        RetentionPolicy.EvictionResult result = policy.evictExpired();
        assertEquals(2, result.evictedBlocks(), "过期块淘汰（cutoff=700000）");
        assertEquals(Set.of(2L), result.clearedSeries(), "无块引用序列清理");
        assertEquals(1, policy.blockCount());
        assertEquals(1, policy.seriesCount());
    }

    @Test
    void BF6_告警状态机与恢复事件() {
        AlertRuleEvaluator evaluator = new AlertRuleEvaluator(
                new AlertRuleEvaluator.Rule("r1", "cpu", true, 90.0, 120_000L));
        assertTrue(evaluator.breaches(95.0));
        assertFalse(evaluator.breaches(80.0));
        assertEquals(AlertRuleEvaluator.State.PENDING,
                evaluator.evaluate("i1", 95.0, 0L).state(), "超阈值但 for 未满 → pending");
        assertEquals(AlertRuleEvaluator.State.PENDING,
                evaluator.evaluate("i1", 95.0, 60_000L).state());
        AlertRuleEvaluator.Evaluation firing = evaluator.evaluate("i1", 95.0, 120_000L);
        assertEquals(AlertRuleEvaluator.State.FIRING, firing.state(), "持续 120s 达标 → firing");
        assertTrue(firing.transition());
        AlertRuleEvaluator.Evaluation resolved = evaluator.evaluate("i1", 50.0, 130_000L);
        assertEquals(AlertRuleEvaluator.State.RESOLVED, resolved.state(), "恢复事件");
        assertEquals("resolved（恢复事件）", resolved.event());
    }

    @Test
    void BF7_样本登记批次幂等() {
        TsSampleRegistry registry = new TsSampleRegistry();
        List<TimeBlocker.Sample> batch = List.of(
                new TimeBlocker.Sample(1_000L, 1),
                new TimeBlocker.Sample(2_000L, 2));
        assertEquals(2, registry.addBatch(7L, batch, "b1"));
        assertEquals(0, registry.addBatch(7L, batch, "b1"), "同批次重放幂等 0 新增");
        assertEquals(1, registry.addBatch(7L, List.of(new TimeBlocker.Sample(2_000L, 9)), "b2"), "跨批次同键亦入");
        assertEquals(3, registry.size());
        assertEquals(3, registry.bySeries(7L).size(), "跨批次同键均保留（幂等仅批内）");
    }
}
