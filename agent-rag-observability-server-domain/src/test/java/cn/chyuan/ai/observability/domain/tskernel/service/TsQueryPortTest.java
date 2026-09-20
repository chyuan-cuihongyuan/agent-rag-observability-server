package cn.chyuan.ai.observability.domain.tskernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查询端口+PromQL 子集单测（工单 0487 BF8）：
 * instant/range 步长对齐/聚合函数子集 by 标签/默认关口径。
 */
class TsQueryPortTest {

    @Test
    void BF8_instant与range步长对齐() {
        TsQueryPort.InMemoryTsQuery query = new TsQueryPort.InMemoryTsQuery();
        query.load(1L, List.of(
                new TimeBlocker.Sample(0L, 1),
                new TimeBlocker.Sample(30_000L, 2),
                new TimeBlocker.Sample(90_000L, 4)));
        assertEquals(2.0, query.instant(1L, 45_000L), 1e-9, "floor 命中 30s 点");
        assertNull(query.instant(2L, 0L), "无序列返回 null");
        List<TimeBlocker.Sample> ranged = query.range(1L, 0L, 120_000L, 30_000L);
        assertEquals(5, ranged.size(), "步长 30s 对齐五个 tick（120s tick floor 命中 90s 点）");
        assertEquals(120_000L, ranged.get(4).timestamp());
        assertEquals(4.0, ranged.get(4).value(), 1e-9, "tick 取 ≤ tick 的最近样本");
        assertThrows(IllegalArgumentException.class, () -> query.range(1L, 0L, 10L, 0L));
    }

    @Test
    void BF8_聚合函数子集按标签() {
        Map<String, Double> summed = TsQueryPort.aggregateByLabels(
                List.of(1.0, 2.0, 10.0, 20.0),
                List.of("prod", "dev", "prod", "dev"), "sum");
        assertEquals(11.0, summed.get("prod"), 1e-9);
        assertEquals(22.0, summed.get("dev"), 1e-9);
        Map<String, Double> averaged = TsQueryPort.aggregateByLabels(
                List.of(1.0, 3.0), List.of("a", "a"), "avg");
        assertEquals(2.0, averaged.get("a"), 1e-9);
        Map<String, Double> minned = TsQueryPort.aggregateByLabels(
                List.of(5.0, 1.0), List.of("x", "x"), "min");
        assertEquals(1.0, minned.get("x"), 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> TsQueryPort.aggregateByLabels(List.of(1.0), List.of("a"), "median"), "非法聚合拒绝");
        assertTrue(TsQueryPort.aggregateByLabels(List.of(), List.of(), "sum").isEmpty());
    }
}
