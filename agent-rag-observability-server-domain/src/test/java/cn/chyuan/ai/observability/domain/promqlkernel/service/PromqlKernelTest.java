package cn.chyuan.ai.observability.domain.promqlkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时序查询内核测试（工单 0899-0906 DB1-DB8，promql 思想）。
 * 指标模型唯一性/label matcher 四算符/instant lookback/range 切片/rate 重置/聚合 by/管线/端口与外部序列联动。
 */
class PromqlKernelTest {

    private static PromModel.Store store() {
        PromModel.Series http = new PromModel.Series("http_requests_total",
                Map.of("job", "gw", "instance", "i1"));
        http.add(1000L, 10);
        http.add(2000L, 15);
        http.add(3000L, 25);
        http.add(60000L, 30);
        PromModel.Series http2 = new PromModel.Series("http_requests_total",
                Map.of("job", "gw", "instance", "i2"));
        http2.add(1000L, 100);
        http2.add(3000L, 160);
        PromModel.Series other = new PromModel.Series("cpu_usage", Map.of("job", "node"));
        other.add(3000L, 0.5);
        return new PromModel.Store().add(http).add(http2).add(other);
    }

    @Test
    void metricModelUniqueness() {
        PromModel.Store store = new PromModel.Store();
        PromModel.Series s = new PromModel.Series("m", Map.of("a", "1"));
        s.add(1L, 1.0);
        store.add(s);
        assertThrows(IllegalArgumentException.class,
                () -> store.add(new PromModel.Series("m", Map.of("a", "1"))), "同键序列唯一性拒绝");
        assertThrows(IllegalArgumentException.class, () -> s.add(0L, 2.0), "时间倒退拒绝");
        assertEquals(1, store.all().size());
    }

    @Test
    void labelMatchers() {
        PromModel.Store store = store();
        var eq = new PromModel.Selector("http_requests_total",
                List.of(new PromModel.Matcher("instance", PromModel.MatchOp.EQ, "i1")), 5000);
        assertEquals(1, eq.select(store).size());
        var neq = new PromModel.Selector("http_requests_total",
                List.of(new PromModel.Matcher("instance", PromModel.MatchOp.NEQ, "i1")), 5000);
        assertEquals(1, neq.select(store).size(), "NEQ 排除 i1");
        var re = new PromModel.Selector("http_requests_total",
                List.of(new PromModel.Matcher("instance", PromModel.MatchOp.RE, "i.")), 5000);
        assertEquals(2, re.select(store).size(), "正则匹配");
        var nre = new PromModel.Selector("http_requests_total",
                List.of(new PromModel.Matcher("instance", PromModel.MatchOp.NRE, "i2")), 5000);
        assertEquals(1, nre.select(store).size());
        assertThrows(IllegalArgumentException.class,
                () -> new PromModel.Matcher("job", PromModel.MatchOp.RE, "[bad"), "非法正则拒绝");
        var none = new PromModel.Selector("http_requests_total",
                List.of(new PromModel.Matcher("pod", PromModel.MatchOp.EQ, "x")), 5000);
        assertTrue(none.select(store).isEmpty(), "缺失标签按空串不匹配");
    }

    @Test
    void instantLookback() {
        PromModel.Store store = store();
        var selector = new PromModel.Selector("http_requests_total", List.of(), 5000);
        List<PromEngine.VectorEntry> at = PromEngine.instant(selector, store, 4000);
        assertEquals(2, at.size(), "4000ms 处 3000 样本在 lookback 内");
        assertEquals(25.0, at.get(0).value());
        List<PromEngine.VectorEntry> stale = PromEngine.instant(selector, store, 50000);
        assertEquals(0, stale.size(), "超窗与晚于评估时刻样本丢弃");
    }

    @Test
    void rangeSlice() {
        PromModel.Store store = store();
        var selector = new PromModel.Selector("http_requests_total", List.of(), 5000);
        List<PromModel.Series> windows = PromEngine.range(selector, store, 1000L, 3000L);
        assertEquals(2, windows.size());
        assertEquals(3, windows.get(0).samples.size(), "窗口内 3 样本");
    }

    @Test
    void rateIncreaseAndReset() {
        PromModel.Series counter = new PromModel.Series("c", Map.of());
        counter.add(1000L, 10);
        counter.add(2000L, 20);
        counter.add(3000L, 5);
        counter.add(4000L, 15);
        Double rate = PromEngine.rate(counter);
        assertEquals(20.0 / 3.0, rate, 1e-9, "重置负差忽略：正增量 10+10=20 / 3s 区间");
        PromModel.Series flat = new PromModel.Series("f", Map.of());
        flat.add(1000L, 5);
        assertNull(PromEngine.rate(flat), "单样本无 rate");
    }

    @Test
    void aggregateByLabel() {
        PromModel.Store store = store();
        var selector = new PromModel.Selector("http_requests_total", List.of(), 5000);
        List<PromEngine.VectorEntry> vector = PromEngine.instant(selector, store, 4000);
        List<PromEngine.VectorEntry> summed = PromEngine.aggregateBy(vector, "job", PromEngine.AggFunc.SUM);
        assertEquals(1, summed.size());
        assertEquals(185.0, summed.get(0).value(), "25+160 latest 相加");
        List<PromEngine.VectorEntry> averaged = PromEngine.aggregateBy(vector, "job", PromEngine.AggFunc.AVG);
        assertEquals(92.5, averaged.get(0).value());
        List<PromEngine.VectorEntry> maxed = PromEngine.aggregateBy(vector, "job", PromEngine.AggFunc.MAX);
        assertEquals(160.0, maxed.get(0).value());
    }

    @Test
    void pipelineSelectRateAggregate() {
        PromModel.Store store = store();
        var selector = new PromModel.Selector("http_requests_total", List.of(), 5000);
        List<PromEngine.VectorEntry> rateSum = PromEngine.query(store, selector, 4000, 3000,
                true, "job", PromEngine.AggFunc.SUM);
        assertEquals(1, rateSum.size());
        double i1 = 15.0 / 2.0;
        double i2 = 60.0 / 2.0;
        assertEquals(37.5, rateSum.get(0).value(), 1e-9, "rate 聚合管线");
        List<PromEngine.VectorEntry> instant = PromEngine.query(store, selector, 4000, 0, false, null, null);
        assertEquals(2, instant.size(), "无 rate 无聚合即 instant");
    }

    @Test
    void portOrchestrationAndExternalLinkage() {
        PromPort port = PromPort.inMemory();
        PromModel.Series s = port.addSeries("errors_total", Map.of("job", "gw"));
        port.ingestExternal(s, List.of(new long[]{1000L, 1L}, new long[]{2000L, 2L}, new long[]{11000L, 5L}));
        var selector = new PromModel.Selector("errors_total", List.of(), 5000);
        var vector = PromEngine.instant(selector, port.store(), 12000);
        assertEquals(1, vector.size());
        assertEquals(5.0, vector.get(0).value(), "tskernel 样本序列形态灌入只读联动");
    }
}
