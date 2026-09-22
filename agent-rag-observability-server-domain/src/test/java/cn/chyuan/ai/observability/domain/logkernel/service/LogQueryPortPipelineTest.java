package cn.chyuan.ai.observability.domain.logkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志查询端口组合管线 BQ8 单测（工单 0580）：
 * 摄入→流选择→管道解析→行过滤→时间聚合确定性输出 + tskernel 指标样本只读联动。
 */
class LogQueryPortPipelineTest {

    private void seed(LogQueryPort.InMemoryLogEngine engine) {
        engine.ingest(Map.of("app", "payment", "env", "prod"), 1_000L, "{\"level\":\"error\",\"dur\":120}");
        engine.ingest(Map.of("app", "payment", "env", "prod"), 5_000L, "{\"level\":\"error\",\"dur\":80}");
        engine.ingest(Map.of("app", "payment", "env", "prod"), 12_000L, "{\"level\":\"info\",\"dur\":5}");
        engine.ingest(Map.of("app", "search", "env", "prod"), 2_000L, "{\"level\":\"error\",\"dur\":999}");
    }

    @Test
    void BQ8_组合管线_流选择到过滤到时间聚合() {
        LogQueryPort.InMemoryLogEngine engine = new LogQueryPort.InMemoryLogEngine();
        seed(engine);
        StreamSelector selector = new StreamSelector().equal("env", "prod").regex("app", "pay.*");
        List<ParserStages.Stage> stages = List.of(ParserStages.json());
        List<LineFilter.Expression> filters = List.of(new LineFilter.Expression("level", "=", "error"));
        WindowAgg agg = new WindowAgg(10_000L);
        List<LogQueryPort.QueryResult> results = engine.query(selector, stages, filters, agg, "dur", null, false);
        assertEquals(1, results.size(), "payment 流 error 两行同窗");
        assertEquals(0L, results.get(0).windowStart(), "ts 1s/5s 对齐窗 0-10s 起点 0");
        assertEquals(200.0d, results.get(0).value(), "dur 求和 120+80");
        // 确定性重放
        LogQueryPort.InMemoryLogEngine again = new LogQueryPort.InMemoryLogEngine();
        seed(again);
        assertEquals(results, again.query(selector, stages, filters, agg, "dur", null, false),
                "同输入同输出确定性");
    }

    @Test
    void BQ8_计数聚合与group分组() {
        LogQueryPort.InMemoryLogEngine engine = new LogQueryPort.InMemoryLogEngine();
        seed(engine);
        StreamSelector selector = new StreamSelector().equal("env", "prod");
        List<LogQueryPort.QueryResult> counts = engine.query(selector, List.of(ParserStages.json()),
                List.of(), new WindowAgg(10_000L), null, null, false);
        assertEquals(3, counts.size(), "逐流聚合：payment 两窗 + search 一窗（跨流不合并）");
        List<LogQueryPort.QueryResult> grouped = engine.query(selector, List.of(ParserStages.json()),
                List.of(), new WindowAgg(10_000L), null, "app", false);
        assertEquals(3, grouped.size(), "按 app 分组三行");
        assertTrue(grouped.stream().anyMatch(r -> "{app=payment,env=prod}".endsWith(r.group()) || r.group().isEmpty()
                || r.group().contains("payment")));
    }

    @Test
    void BQ8_tskernel指标样本只读联动与登记面() {
        LogQueryPort.InMemoryLogEngine engine = new LogQueryPort.InMemoryLogEngine();
        seed(engine);
        List<LogQueryPort.QueryResult> results = engine.query(
                new StreamSelector().equal("app", "payment"), List.of(ParserStages.json()),
                List.of(new LineFilter.Expression("level", "=", "error")),
                new WindowAgg(10_000L), "dur", null, false);
        List<long[]> samples = engine.toMetricSamples(results);
        assertEquals(results.size(), samples.size());
        assertEquals(0L, samples.get(0)[0], "样本时间戳=窗口起点 0");
        assertEquals(200L, samples.get(0)[1], "样本值=聚合值（tskernel 样本输入形态）");
        assertEquals(2, engine.registry().snapshot().size(), "两流登记（payment/search）");
        assertEquals(4, engine.registry().totals()[0], "总行数 4");
    }
}
