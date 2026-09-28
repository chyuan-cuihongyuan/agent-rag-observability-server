package cn.chyuan.ai.observability.domain.tracekernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分布式追踪内核测试（工单 0968-0975 EE1-EE8，jaeger 思想）。
 * traceparent 解析生成/span 开闭/span 树补根/时钟偏斜/概率限速采样/悬挂回填孤儿/依赖 DAG/端口查询联动。
 */
class TraceKernelTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    void traceparentParseAndGenerate() {
        TraceContext ctx = TraceContext.parse(TRACEPARENT);
        assertEquals("00", ctx.version);
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ctx.traceId);
        assertEquals("00f067aa0ba902b7", ctx.spanId);
        assertTrue(ctx.sampled());
        assertEquals(TRACEPARENT, ctx.traceparent(), "往返一致");

        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(null), "空拒绝");
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse("00-abc-00f067aa0ba902b7-01"), "段长非法拒绝");
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01"), "全零追踪 id 拒绝");
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(
                "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"), "ff 版本拒绝");
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e473g-00f067aa0ba902b7-01"), "非 hex 拒绝");

        Random random = new Random(7);
        TraceContext generated = TraceContext.generate(random);
        assertEquals("00", generated.version);
        assertTrue(generated.sampled());
        assertEquals(generated.traceparent(), TraceContext.parse(generated.traceparent()).traceparent(), "生成可解析");
    }

    @Test
    void spanLifecycleAndDuration() {
        TraceStore store = new TraceStore(2);
        store.start("t1", "s1", null, "web", "GET /", 100L);
        store.start("t1", "s2", "s1", "db", "SELECT", 120L);
        assertThrows(IllegalArgumentException.class, () -> store.start("t1", "s1", null, "web", "GET /", 100L), "重复 spanId 拒绝");
        assertThrows(IllegalArgumentException.class, () -> store.duration("s1"), "未关闭拒绝统计");
        store.finish("s1", 260L);
        assertThrows(IllegalArgumentException.class, () -> store.finish("s1", 300L), "重复关闭拒绝");
        assertEquals(160L, store.duration("s1"));
        assertThrows(IllegalArgumentException.class, () -> store.finish("ghost", 1L), "未知 span 拒绝");
        store.finish("s2", 200L);
        assertEquals(80L, store.duration("s2"));
    }

    @Test
    void spanTreeRootsAndOrdering() {
        TraceStore store = new TraceStore(2);
        store.start("t1", "rootB", null, "web", "bbb", 100L);
        store.start("t1", "child1", "rootB", "db", "aaa", 110L);
        store.start("t1", "child2", "rootB", "cache", "ccc", 105L);
        store.start("t1", "lost", "missingParent", "web", "aaa", 90L);
        store.start("t1", "rootA", null, "web", "aaa", 80L);
        List<TraceStore.Node> roots = store.buildTree();
        assertEquals(3, roots.size(), "缺父补根（lost 父缺失）");
        assertEquals("aaa", roots.get(0).span.name, "根按 name 排序");
        assertEquals("aaa", roots.get(1).span.name, "同名按 spanId 排序");
        assertEquals("rootA", roots.get(1).span.spanId);
        assertEquals("bbb", roots.get(2).span.name);
        List<TraceStore.Node> children = roots.get(2).children;
        assertEquals(2, children.size());
        assertEquals("child2", children.get(0).span.spanId, "子按 start 排序");
        assertEquals("child1", children.get(1).span.spanId);
    }

    @Test
    void clockSkewAdjustment() {
        TraceStore store = new TraceStore(2);
        store.start("t1", "p", null, "web", "op", 1000L);
        store.start("t1", "c", "p", "db", "query", 800L);
        store.adjustSkew();
        assertEquals(1000L, store.get("c").startMicros, "子开始前移至父开始");
        assertEquals(200L, store.get("c").skewAdjustMicros, "校正量记录");
        assertEquals(800L, store.get("c").originalStartMicros, "真实序保留");
        store.adjustSkew();
        assertEquals(1000L, store.get("c").startMicros, "重复校正幂等");
    }

    @Test
    void samplingProbabilityAndRateLimit() {
        Sampler prob = new Sampler(0.0, null);
        assertEquals("skeleton", prob.decision("any-trace"), "概率 0 全走骨架");
        Sampler all = new Sampler(1.0, null);
        assertEquals("full", all.decision("any-trace"), "概率 1 全采样");
        assertThrows(IllegalArgumentException.class, () -> new Sampler(1.5, null), "概率非法拒绝");

        Sampler.RateLimiter limiter = new Sampler.RateLimiter(2, 1);
        Sampler limited = new Sampler(1.0, limiter);
        assertTrue(limited.sample("t1"));
        assertTrue(limited.sample("t2"));
        assertFalse(limited.sample("t3"), "令牌耗尽拒绝");
        assertEquals(0.0, limiter.tokens());
        limiter.tick();
        assertTrue(limited.sample("t4"), "补给后再取");
        assertEquals(0.0, limiter.tokens(), "补给封顶容量");
        assertThrows(IllegalArgumentException.class, () -> new Sampler.RateLimiter(0, 1), "容量非法拒绝");
    }

    @Test
    void orphanParkAndReparent() {
        TraceStore store = new TraceStore(2);
        store.start("t1", "child", "lateParent", "db", "query", 100L);
        assertTrue(store.parked(store.get("child")), "父未到先悬挂");
        assertFalse(store.orphan(store.get("child")));
        store.tick();
        assertTrue(store.parked(store.get("child")), "窗口内仍悬挂");
        store.start("t1", "lateParent", null, "web", "op", 90L);
        assertFalse(store.parked(store.get("child")), "父到回填");
        assertTrue(store.buildTree().stream().anyMatch(n -> n.span.spanId.equals("lateParent")
                && n.children.stream().anyMatch(c -> c.span.spanId.equals("child"))), "回填挂到父下");

        TraceStore orphaned = new TraceStore(2);
        orphaned.start("t2", "orphanChild", "neverArrives", "db", "query", 100L);
        orphaned.tick();
        orphaned.tick();
        assertTrue(orphaned.orphan(orphaned.get("orphanChild")), "超窗判孤儿");
        assertTrue(orphaned.buildTree().stream().anyMatch(n -> n.span.spanId.equals("orphanChild")), "孤儿补为根");
    }

    @Test
    void dependencyDagCountingAndOrder() {
        DependencyDag dag = new DependencyDag();
        dag.record("web", "db");
        dag.record("web", "db");
        dag.record("db", "cache");
        dag.record("web", "web");
        assertEquals(0, dag.calls("web", "web"), "自环排除");
        assertEquals(2, dag.calls("web", "db"));
        assertEquals(2, dag.size());
        List<DependencyDag.Edge> edges = dag.edges();
        assertEquals("db", edges.get(0).client, "按 client 字典序");
        assertEquals("web", edges.get(1).client);
        assertEquals(2, edges.get(1).calls);
    }

    @Test
    void portQueryAndSamplesLinkage() {
        TracePort port = TracePort.inMemory();
        port.start("web", "op", "t1", "s1", null, 100L);
        port.start("db", "query", "t1", "s2", "s1", 120L);
        port.finish("s1", 300L);
        port.finish("s2", 240L);

        List<TracePort.SpanView> all = port.query(null, null, null);
        assertEquals(2, all.size());
        assertEquals(100L, all.get(0).startMicros(), "按开始时间排序");
        assertEquals(200L, all.get(0).durationMicros());

        assertEquals(1, port.query("db", null, null).size(), "按服务过滤");
        assertEquals(1, port.query(null, "query", null).size(), "按操作过滤");
        List<TracePort.SpanView> slow = port.query(null, null, 150L);
        assertEquals(1, slow.size(), "按时长过滤");
        assertEquals("s1", slow.get(0).spanId());
        assertThrows(IllegalArgumentException.class, () -> port.samplesOf("ghost"), "未知 span 采样拒绝");

        long[] samples = port.samplesOf("s1");
        assertArrayEquals(new long[]{100L, 300L, 200L}, samples, "tskernel 样本序列形态只读联动");

        port.start("db", "pending", "t1", "s3", "missing", 50L);
        port.tick();
        port.tick();
        assertTrue(port.query(null, "pending", null).get(0).orphan(), "查询暴露孤儿标记");
    }
}
