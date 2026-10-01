package cn.chyuan.ai.observability.domain.aggkernel.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聚合分析内核测试（工单 1212-1219 FG1-FG8，elasticsearch 思想）。
 * 指标聚合/terms 分桶/直方图/嵌套聚合/管道聚合/过滤聚合/排序截断/端口组合管线。
 */
class AggKernelTest {

    private AggPort port() {
        AggPort port = AggPort.inMemory();
        port.add(Map.of("service", "a", "region", "hz", "latency", 100, "errors", 1));
        port.add(Map.of("service", "a", "region", "sh", "latency", 200));
        port.add(Map.of("service", "b", "region", "hz", "latency", 50, "errors", 2));
        port.add(Map.of("service", "c", "region", "sh", "latency", 150, "errors", 3));
        return port;
    }

    @Test
    void metricAgg() {
        AggPort port = port();
        assertEquals(500.0, port.run(new Agg.Metric("sum", Agg.Func.SUM, "latency")).metric("sum"));
        assertEquals(125.0, port.run(new Agg.Metric("avg", Agg.Func.AVG, "latency")).metric("avg"));
        assertEquals(50.0, port.run(new Agg.Metric("min", Agg.Func.MIN, "latency")).metric("min"));
        assertEquals(200.0, port.run(new Agg.Metric("max", Agg.Func.MAX, "latency")).metric("max"));
        assertEquals(3.0, port.run(new Agg.Metric("cnt", Agg.Func.COUNT, "errors")).metric("cnt"),
                "缺失字段文档跳过");
        BucketResult empty = AggPort.inMemory().run(new Agg.Metric("sum", Agg.Func.SUM, "latency"));
        assertEquals(0, empty.docCount(), "空文档集空结果");
        assertNull(empty.metric("sum"));
        port.add(Map.of("service", "d", "latency", "fast"));
        assertThrows(IllegalArgumentException.class,
                () -> port.run(new Agg.Metric("sum2", Agg.Func.SUM, "latency")), "非数值字段拒绝");
    }

    @Test
    void termsBucket() {
        AggPort port = port();
        BucketResult result = port.run(new Agg.Terms("by_service", "service", 10, Agg.Sort.COUNT_DESC));
        assertEquals(4, result.docCount());
        List<BucketResult> buckets = result.buckets("by_service");
        assertEquals(List.of("a", "b", "c"), buckets.stream().map(BucketResult::key).toList(),
                "计数降序 a:2 b:1 c:1");
        BucketResult capped = port.run(new Agg.Terms("by_service", "service", 2, Agg.Sort.COUNT_DESC));
        assertEquals(2, capped.buckets("by_service").size(), "size 截断");
        assertEquals(1, port.docs().stream().filter(d -> d.get("errors") == null).count(),
                "缺失字段文档存在但不入桶");
    }

    @Test
    void histogram() {
        AggPort port = port();
        BucketResult sparse = port.run(new Agg.Histogram("h", "latency", 100, false));
        List<BucketResult> buckets = sparse.buckets("h");
        assertEquals(List.of("0", "100", "200"), buckets.stream().map(BucketResult::key).toList(),
                "区间键取整倍数空桶不产出");
        assertEquals(1, buckets.get(0).docCount());
        assertEquals(2, buckets.get(1).docCount(), "100/150 落 [100,200)");

        AggPort filled = AggPort.inMemory();
        filled.add(Map.of("latency", 0));
        filled.add(Map.of("latency", 300));
        List<BucketResult> all = filled.run(new Agg.Histogram("h", "latency", 100, true))
                .buckets("h");
        assertEquals(List.of("0", "100", "200", "300"), all.stream().map(BucketResult::key).toList(),
                "min_doc_count 连续补零");
        assertEquals(0, all.get(1).docCount());
        AggPort bad = AggPort.inMemory();
        bad.add(Map.of("latency", 1));
        assertThrows(IllegalArgumentException.class,
                () -> bad.run(new Agg.Histogram("h", "latency", 0, false)), "interval 须为正");
    }

    @Test
    void nestedAgg() {
        AggPort port = port();
        BucketResult result = port.run(new Agg.Terms("by_service", "service", 10, Agg.Sort.COUNT_DESC,
                List.of(new Agg.Metric("avg_latency", Agg.Func.AVG, "latency"),
                        new Agg.Terms("by_region", "region", 10, Agg.Sort.KEY_ASC))));
        BucketResult a = result.buckets("by_service").get(0);
        assertEquals("a", a.key());
        assertEquals(150.0, a.metric("avg_latency"), "桶内嵌套指标");
        assertEquals(List.of("hz", "sh"),
                a.buckets("by_region").stream().map(BucketResult::key).toList(), "桶内嵌套子桶");
        assertEquals(1, a.buckets("by_region").get(0).docCount());
    }

    @Test
    void pipelineAgg() {
        List<Double> sums = List.of(50.0, 250.0, 200.0);
        assertEquals(Arrays.asList(null, 200.0, -50.0), Pipelines.derivative(sums), "求导首桶无前驱");
        assertEquals(List.of(50.0, 300.0, 500.0), Pipelines.cumulativeSum(sums), "累积和");
        assertEquals(166.66666666666666, Pipelines.avgBucket(sums), "兄弟桶均值");
        assertThrows(IllegalArgumentException.class, () -> Pipelines.avgBucket(List.of()), "空父桶拒绝");
        assertEquals(List.of(), Pipelines.derivative(List.of()));
    }

    @Test
    void filterAgg() {
        AggPort port = port();
        BucketResult filtered = port.run(new Agg.Filter("hz_only", "region", "hz",
                List.of(new Agg.Metric("sum_errors", Agg.Func.SUM, "errors"))));
        assertEquals(2, filtered.docCount(), "filter 只收匹配文档");
        assertEquals(3.0, filtered.metric("sum_errors"));

        BucketResult missing = port.run(new Agg.Missing("no_errors", "errors", List.of()));
        assertEquals(1, missing.docCount(), "missing 收缺失字段文档");

        BucketResult global = port.run(new Agg.Filter("hz_only", "region", "hz",
                List.of(new Agg.Global("all_docs", List.of()))));
        BucketResult inner = global.buckets("all_docs").get(0);
        assertEquals(2, global.docCount());
        assertEquals(4, inner.docCount(), "global 全集无视上层过滤");
    }

    @Test
    void sortTruncate() {
        AggPort port = AggPort.inMemory();
        port.add(Map.of("zone", "x", "v", 1));
        port.add(Map.of("zone", "y", "v", 1));
        port.add(Map.of("zone", "z", "v", 5));
        port.add(Map.of("zone", "z", "v", 6));
        port.add(Map.of("zone", "z", "v", 7));
        assertEquals(List.of("z", "x", "y"),
                keys(port, new Agg.Terms("t", "zone", 10, Agg.Sort.COUNT_DESC)), "计数降序");
        assertEquals(List.of("x", "y", "z"),
                keys(port, new Agg.Terms("t", "zone", 10, Agg.Sort.COUNT_ASC)), "计数升序");
        assertEquals(List.of("x", "y", "z"),
                keys(port, new Agg.Terms("t", "zone", 10, Agg.Sort.KEY_ASC)), "键升序");
        assertEquals(List.of("z", "y", "x"),
                keys(port, new Agg.Terms("t", "zone", 10, Agg.Sort.KEY_DESC)), "键降序");
        assertEquals(2,
                port.run(new Agg.Terms("t", "zone", 2, Agg.Sort.KEY_ASC)).buckets("t").size(),
                "截断留痕");
        assertThrows(IllegalArgumentException.class,
                () -> port.run(new Agg.Terms("t", "zone", 0, Agg.Sort.KEY_ASC)), "size 须为正");
    }

    private List<String> keys(AggPort port, Agg agg) {
        return port.run(agg).buckets("t").stream().map(BucketResult::key).toList();
    }

    @Test
    void aggPipeline() {
        AggPort port = port();
        BucketResult histogram = port.run(new Agg.Histogram("by_latency", "latency", 100, false,
                List.of(new Agg.Metric("sum_latency", Agg.Func.SUM, "latency"))));
        List<Double> sums = histogram.buckets("by_latency").stream()
                .map(b -> b.metric("sum_latency"))
                .map(v -> v == null ? 0.0 : v)
                .toList();
        assertEquals(List.of(50.0, 250.0, 200.0), sums);
        assertEquals(List.of(50.0, 300.0, 500.0), Pipelines.cumulativeSum(sums), "管道接管桶指标");
        assertEquals(List.of("metric", "labels", "samples"), port.seriesShape(),
                "promqlkernel 序列形状只读联动");
    }
}
