package cn.chyuan.ai.observability.domain.logkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志检索内核 BQ1-BQ7 单测（工单 0573-0579）：
 * 标签流模型/流选择器/管道解析阶段/行过滤格式化/时间窗聚合/块存储/流登记表。
 */
class LogKernelTest {

    private Map<String, String> labels(String app, String env) {
        return Map.of("app", app, "env", env);
    }

    @Test
    void BQ1_日志流模型与流指纹() {
        assertEquals("{app=a,env=prod}", LogStreams.streamKey(labels("a", "prod")), "字典序流键");
        assertEquals(LogStreams.DEFAULT_STREAM, LogStreams.streamKey(Map.of()), "空标签默认流");
        assertEquals(LogStreams.fingerprint(LogStreams.streamKey(labels("a", "prod"))),
                LogStreams.fingerprint("{app=a,env=prod}"), "指纹对等价键稳定");
        assertNotEqualsFingerprint(LogStreams.streamKey(labels("a", "prod")),
                LogStreams.streamKey(labels("a", "dev")));
        LogStreams.StreamSet set = new LogStreams.StreamSet();
        set.ingest(labels("a", "prod"), 100L, "line-1");
        set.ingest(labels("a", "prod"), 200L, "line-2");
        set.ingest(labels("a", "dev"), 300L, "line-3");
        assertEquals(2, set.streamCount());
        assertEquals(3, set.totalLines());
        LogStreams.Stream stream = set.stream("{app=a,env=prod}");
        assertEquals(2, stream.lineCount());
        assertEquals(12, stream.byteCount());
        assertEquals(100L, stream.entries().get(0).ts(), "流内行追加有序");
        assertThrows(IllegalArgumentException.class, () -> stream.append(-1L, "x"), "负时间戳拒绝");
        assertThrows(IllegalArgumentException.class, () -> stream.append(1L, ""), "空行拒绝");
    }

    private void assertNotEqualsFingerprint(String a, String b) {
        assertFalse(LogStreams.fingerprint(a) == LogStreams.fingerprint(b), "不同流指纹不同");
    }

    @Test
    void BQ2_LogQL流选择器等值与正则交集() {
        StreamSelector selector = new StreamSelector()
                .equal("env", "prod")
                .regex("app", "pay.*");
        assertTrue(selector.matches(labels("payment", "prod")), "等值+正则交集命中");
        assertFalse(selector.matches(labels("payment", "dev")), "等值不中");
        assertFalse(selector.matches(labels("other", "prod")), "正则不中");
        assertFalse(selector.matches(Map.of("env", "prod")), "缺标签不命中");
        Map<String, Map<String, String>> streams = Map.of(
                "{app=payment,env=prod}", labels("payment", "prod"),
                "{app=search,env=prod}", labels("search", "prod"));
        assertEquals(List.of("{app=payment,env=prod}"), selector.select(streams), "未匹配流裁剪");
        assertEquals(2, selector.matchers().size());
        assertThrows(IllegalArgumentException.class, () -> selector.regex("app", "[invalid"),
                "非法正则拒绝");
        assertThrows(IllegalArgumentException.class, () -> selector.equal("", "x"), "空标签名拒绝");
    }

    @Test
    void BQ3_管道解析三阶段() {
        ParserStages parser = new ParserStages();
        // json 阶段
        List<ParserStages.ParsedLine> json = parser.run(
                List.of("{\"level\":\"error\",\"dur\":120}", "not-json"),
                List.of(ParserStages.json()));
        assertEquals("error", json.get(0).extracted().get("level"));
        assertEquals("120", json.get(0).extracted().get("dur"));
        assertTrue(json.get(1).extracted().isEmpty(), "解析失败行保留原行空字段");
        // regexp 命名捕获
        List<ParserStages.ParsedLine> regex = parser.run(
                List.of("GET /api 200"),
                List.of(ParserStages.regexp("(?<method>\\w+) (?<path>\\S+) (?<code>\\d+)")));
        assertEquals("/api", regex.get(0).extracted().get("path"));
        // pattern 模式
        List<ParserStages.ParsedLine> pattern = parser.run(
                List.of("INFO payment handled"),
                List.of(ParserStages.pattern("<level> <svc> <msg>")));
        assertEquals("INFO", pattern.get(0).extracted().get("level"));
        assertEquals("payment", pattern.get(0).extracted().get("svc"));
        assertEquals("handled", pattern.get(0).extracted().get("msg"), "末位占位吞余词");
        // 字面量不一致
        List<ParserStages.ParsedLine> mismatch = parser.run(
                List.of("WARN x y"),
                List.of(ParserStages.pattern("<level> fixed <rest>")));
        assertTrue(mismatch.get(0).extracted().isEmpty(), "字面量不匹配空字段");
    }

    @Test
    void BQ4_行过滤与labelFormat() {
        ParserStages parser = new ParserStages();
        List<ParserStages.ParsedLine> parsed = parser.run(
                List.of("{\"level\":\"error\",\"dur\":120}", "{\"level\":\"info\",\"dur\":5}",
                        "{\"level\":\"error\",\"dur\":1}"),
                List.of(ParserStages.json()));
        LineFilter filter = new LineFilter();
        List<LineFilter.Expression> expressions = List.of(
                new LineFilter.Expression("level", "=", "error"),
                new LineFilter.Expression("dur", ">", "10"));
        List<ParserStages.ParsedLine> kept = filter.filter(parsed, expressions);
        assertEquals(1, kept.size(), "AND 过滤只剩 error 且 dur>10");
        assertTrue(kept.get(0).line().contains("120"));
        assertFalse(filter.keep(parsed.get(0), new LineFilter.Expression("missing", "=", "x")),
                "缺字段不命中");
        Map<String, String> formatted = filter.labelFormat(
                Map.of("env", "prod"),
                Map.of("env", "environment"),
                Map.of("summary", "{level}@{dur}"),
                parsed.get(0));
        assertEquals("prod", formatted.get("environment"), "标签重命名");
        assertEquals("error@120", formatted.get("summary"), "派生字段模板求值");
        assertThrows(IllegalArgumentException.class,
                () -> filter.keep(parsed.get(0), new LineFilter.Expression("level", "~", "x")),
                "非法算子拒绝");
    }

    @Test
    void BQ5_时间窗聚合与补零() {
        WindowAgg agg = new WindowAgg(10_000L);
        assertEquals(10_000L, agg.align(12_345L), "窗口对齐 floorDiv");
        List<WindowAgg.Measurement> measurements = List.of(
                new WindowAgg.Measurement(1_000L, "a", 1.0d),
                new WindowAgg.Measurement(5_000L, "a", 2.0d),
                new WindowAgg.Measurement(12_000L, "a", 4.0d),
                new WindowAgg.Measurement(15_000L, "b", 8.0d));
        List<WindowAgg.AggRow> summed = agg.aggregate(measurements, WindowAgg.Func.SUM, "grp", true);
        assertEquals(4, summed.size(), "两窗两组，补零一窗");
        assertEquals(3.0d, summed.get(0).value(), "窗 0 组 a sum=3");
        assertEquals(0.0d, summed.get(1).value(), "窗 0 组 b 补零");
        assertEquals(4.0d, summed.get(2).value(), "窗 10s 组 a");
        assertEquals(8.0d, summed.get(3).value(), "窗 10s 组 b");
        List<WindowAgg.AggRow> counted = agg.aggregate(measurements, WindowAgg.Func.COUNT, null, false);
        assertEquals(2, counted.size(), "全局单组计数");
        assertEquals(2, (long) counted.get(0).value());
        List<WindowAgg.AggRow> averaged = agg.aggregate(measurements, WindowAgg.Func.AVG, null, false);
        assertEquals(1.5d, averaged.get(0).value(), "窗 0 全局 avg=(1+2)/2");
        assertThrows(IllegalArgumentException.class, () -> new WindowAgg(0L), "零窗口拒绝");
    }

    @Test
    void BQ6_块存储与时间裁剪() {
        BlockStore store = new BlockStore(10_000L);
        store.append("{app=a}", 1_000L, "l1");
        store.append("{app=a}", 9_000L, "l2");
        store.append("{app=a}", 21_000L, "l3");
        assertEquals(1, store.streamCount());
        assertEquals(2, store.blockCount(), "两个时间窗两块");
        assertEquals(2, store.query("{app=a}", 0L, 10_000L).size(), "块索引裁剪命中首块");
        assertEquals(1, store.query("{app=a}", 20_000L, 30_000L).size());
        assertEquals(0, store.query("{app=a}", 55_000L, 60_000L).size(), "范围外空结果");
        assertEquals(3, store.query("{app=a}", 0L, 99_999L).size(), "全范围归并");
        assertArrayEquals(new long[]{3, 6}, store.streamStats("{app=a}"));
        assertThrows(IllegalArgumentException.class, () -> store.query("{app=a}", 10L, 5L),
                "from>to 拒绝");
        assertThrows(IllegalArgumentException.class, () -> new BlockStore(0L), "零块窗拒绝");
    }

    private void assertArrayEquals(long[] expected, long[] actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
    }

    @Test
    void BQ7_流登记表合并统计与墓碑() {
        LogStreamRegistry registry = new LogStreamRegistry();
        registry.ingest("{app=a,env=prod}", 2, 20L, 100L, 200L);
        registry.ingest("{app=a,env=prod}", 1, 10L, 50L, 300L);
        assertEquals(1, registry.snapshot().size(), "同指纹合并为一行");
        LogStreamRegistry.LogStreamRow row = registry.snapshot().get(0);
        assertEquals(3, row.lineCount(), "行数累加");
        assertEquals(30L, row.byteCount());
        assertEquals(50L, row.firstTs(), "首时间戳取小");
        assertEquals(300L, row.lastTs(), "末时间戳取大");
        assertEquals(LogStreamRegistry.STATUS_ACTIVE, row.status());
        assertTrue(registry.contains("{app=a,env=prod}"));
        registry.delete("{app=a,env=prod}");
        assertFalse(registry.contains("{app=a,env=prod}"), "墓碑后不活跃");
        assertThrows(IllegalArgumentException.class,
                () -> registry.ingest("{app=a,env=prod}", 1, 1L, 1L, 1L), "已注销流拒绝摄入");
        assertThrows(IllegalArgumentException.class, () -> registry.delete("{ghost}"), "流不存在拒绝");
        assertThrows(IllegalArgumentException.class, () -> registry.ingest("", 1, 1L, 1L, 1L),
                "空流键拒绝");
    }
}
