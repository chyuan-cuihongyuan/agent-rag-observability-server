package cn.chyuan.ai.observability.domain.pipekernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 拓扑管道内核测试（工单 0976-0983 EF1-EF8，vector 思想）。
 * 拓扑解析闭合/事件模型跨型/VRL 三语句/有界缓冲/ack 水位/模板路由/端口端到端泵跑。
 */
class PipeKernelTest {

    private static final String TOPOLOGY = """
            [source]
            in = demo

            [transform]
            enrich = remap, inputs=in

            [sink]
            out = log_console, inputs=enrich
            """;

    @Test
    void topologyParseAndFormatReject() {
        Topology topology = Topology.parse(TOPOLOGY);
        assertEquals(3, topology.all().size());
        assertEquals("source", topology.get("in").kind);
        assertEquals(List.of("in"), topology.get("enrich").inputs);

        assertThrows(IllegalArgumentException.class, () -> Topology.parse("in = demo"), "节外声明拒绝");
        assertThrows(IllegalArgumentException.class, () -> Topology.parse("[unknown]\na = b"), "未知节拒绝");
        assertThrows(IllegalArgumentException.class, () -> Topology.parse("[source]\na "), "缺 = 拒绝");
        assertThrows(IllegalArgumentException.class, () -> Topology.parse("[source]\na = demo\na = other"), "重复节点拒绝");
        assertThrows(IllegalArgumentException.class, () -> Topology.parse("[source]\na = demo, inputs=b"), "source 带输入拒绝");
        assertThrows(IllegalArgumentException.class, () -> Topology.parse(""), "空拓扑拒绝");
    }

    @Test
    void topologyClosureValidation() {
        assertThrows(IllegalArgumentException.class, () -> Topology.parse(
                "[transform]\nt = remap, inputs=ghost").validate(), "引用未定义拒绝");
        assertThrows(IllegalArgumentException.class, () -> Topology.parse(
                "[source]\nin = demo\n[transform]\nalone = remap, inputs=in").validate(), "孤儿节点拒绝");
        assertThrows(IllegalArgumentException.class, () -> Topology.parse(
                "[transform]\nt = remap, inputs=t2\n[transform]\nt2 = remap, inputs=t\n[sink]\nout = console, inputs=t").validate(),
                "环拒绝");
        Topology.parse(TOPOLOGY).validate();
    }

    @Test
    void eventModelAndTypeGuard() {
        Event log = Event.log("hello", Map.of("team", "pay"));
        assertEquals("hello", log.field("message"));
        assertEquals("pay", log.field("team"));
        Event metric = Event.metric("qps", 3.5);
        assertEquals("3.5", metric.field("value"));
        assertThrows(IllegalArgumentException.class, () -> Event.log("", Map.of()), "空 message 拒绝");
        assertThrows(IllegalArgumentException.class, () -> Event.metric("", 1), "空 name 拒绝");
        assertThrows(IllegalArgumentException.class, () -> log.requireMetric(), "跨型拒绝 log≠metric");
        assertThrows(IllegalArgumentException.class, () -> metric.requireLog(), "跨型拒绝 metric≠log");
    }

    @Test
    void vrlStatementsAndReject() {
        Vrl vrl = Vrl.compile(List.of(
                ".team = \"pay\"",
                ".score = 42",
                "del(.temp)",
                "if .secret == \"yes\" { drop() }"));
        assertEquals(4, vrl.size());
        Event event = Event.log("m", Map.of("temp", "x", "secret", "no"));
        Event out = vrl.apply(event);
        assertEquals("pay", out.field("team"), "字符串赋值");
        assertEquals("42", out.field("score"), "整数赋值");
        assertNull(out.field("temp"), "del 删除");
        assertNotNull(out, "条件未命中保留");

        assertEquals(null, vrl.apply(Event.log("m", Map.of("secret", "yes"))), "条件命中丢弃");

        assertThrows(IllegalArgumentException.class, () -> Vrl.compile(List.of("foo(.x)")), "未知语句拒绝");
        assertThrows(IllegalArgumentException.class, () -> Vrl.compile(List.of(".x = symbol")), "非法字面量拒绝");
        assertThrows(IllegalArgumentException.class, () -> Vrl.compile(List.of("x.y = 1")), "路径未点起头拒绝");
    }

    @Test
    void boundedBufferOverflowReject() {
        Buffer buffer = new Buffer(2);
        buffer.push("a");
        buffer.push("b");
        assertThrows(IllegalStateException.class, () -> buffer.push("c"), "满时拒绝");
        assertEquals(2, buffer.size());
        assertEquals("a", buffer.poll());
        buffer.push("c");
        assertEquals(2, buffer.size(), "出队后可再入");
        assertThrows(IllegalArgumentException.class, () -> new Buffer(0), "容量非法拒绝");
    }

    @Test
    void ackWatermarkContiguousAdvance() {
        AckWatermark watermark = new AckWatermark();
        watermark.ack(2);
        assertEquals(0, watermark.watermark(), "乱序确认不推进");
        assertEquals(1, watermark.aheadCount());
        watermark.ack(1);
        assertEquals(2, watermark.watermark(), "补齐后推进最小连续");
        watermark.ack(4);
        watermark.ack(3);
        assertEquals(4, watermark.watermark());
        assertThrows(IllegalArgumentException.class, () -> watermark.ack(4), "重复确认拒绝");
        assertThrows(IllegalArgumentException.class, () -> watermark.ack(2), "已过时确认拒绝");
    }

    @Test
    void routeTemplateRenderAndDispatch() {
        Router router = new Router();
        router.route("out", "lane/{{.team}}");
        assertEquals("lane/pay", router.render("lane/{{.team}}", Event.log("m", Map.of("team", "pay"))));
        assertThrows(IllegalArgumentException.class, () -> router.render("lane/{{.missing}}", Event.log("m", Map.of())),
                "缺失变量拒绝");
        assertThrows(IllegalArgumentException.class, () -> router.render("lane/{{.team", Event.log("m", Map.of())),
                "未闭合拒绝");
        router.dispatch("out", Event.log("m1", Map.of("team", "pay")));
        router.dispatch("out", Event.log("m2", Map.of("team", "ops")));
        router.dispatch("out", Event.log("m3", Map.of("team", "pay")));
        assertEquals(2, router.laneCount(), "按键分发");
        assertEquals(2, router.lane("lane/pay").size());
        assertEquals(1, router.lane("lane/ops").size());
    }

    @Test
    void portEndToEndPump() {
        PipePort port = PipePort.inMemory();
        port.load(TOPOLOGY);
        port.program("enrich", List.of(".team = \"pay\"", "if .drop_me == \"yes\" { drop() }"));
        port.routeSink("out", "lane/{{.team}}");

        long seq = port.pump("in", Event.log("order-1", Map.of()));
        assertEquals(1, seq);
        assertEquals(1, port.lane("lane/pay").size());
        assertEquals("order-1", port.lane("lane/pay").get(0).field("message"), "变换与路由落地");

        assertEquals(-1, port.pump("in", Event.log("skip", Map.of("drop_me", "yes"))), "条件丢弃返回 -1");
        assertEquals(1, port.pending(), "丢弃不进待确认");

        assertThrows(IllegalArgumentException.class, () -> port.pump("ghost", Event.log("x", Map.of())), "未知来源拒绝");
        assertThrows(IllegalStateException.class, () -> PipePort.inMemory().pump("in", Event.log("x", Map.of())),
                "拓扑未加载拒绝");

        port.ack(1);
        assertEquals(1, port.watermark(), "确认推进水位");
        assertEquals(0, port.pending());
        assertThrows(IllegalArgumentException.class, () -> port.ack(1), "重复确认拒绝");

        PipePort metricPipe = PipePort.inMemory();
        metricPipe.load("[source]\nin = demo\n[sink]\nout = log_console, inputs=in".replace("log_console", "metric_sink"));
        assertThrows(IllegalArgumentException.class, () -> metricPipe.pump("in", Event.log("x", Map.of())),
                "sink 跨型拒绝");

        assertEquals("log://order-1", PipePort.recordOf(Event.log("order-1", Map.of())), "logkernel 记录形态只读联动");
        assertEquals("metric://qps", PipePort.recordOf(Event.metric("qps", 1)), "metric 记录形态");
    }
}
