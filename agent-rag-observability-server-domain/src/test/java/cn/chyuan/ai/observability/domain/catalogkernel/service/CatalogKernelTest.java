package cn.chyuan.ai.observability.domain.catalogkernel.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务目录内核测试（工单 1090-1097 ES1-ES8，consul 思想）。
 * 两级注册目录/健康聚合/阻塞查询/prepared query/session TTL/锁与信号量/反熵收敛/端口组合管线。
 */
class CatalogKernelTest {

    @Test
    void catalogRegistration() {
        Catalogs catalogs = new Catalogs();
        assertTrue(catalogs.register("n1", "web"));
        assertFalse(catalogs.register("n1", "web"), "重复注册幂等");
        assertTrue(catalogs.register("n1", "api"), "同节点多服务并存");
        assertTrue(catalogs.register("n2", "web"));
        assertEquals(List.of("n1", "n2"), catalogs.services("web"));
        assertEquals(2, catalogs.deregisterNode("n1"), "注销节点级联摘除服务");
        assertEquals(List.of("n2"), catalogs.services("web"));
        assertTrue(catalogs.services("api").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> catalogs.deregisterNode("ghost"));
        assertThrows(IllegalArgumentException.class, () -> catalogs.deregisterService("n2", "ghost"));
        assertEquals(List.of("n2/web"), catalogs.snapshot());
    }

    @Test
    void healthAggregation() {
        HealthChecks checks = new HealthChecks();
        checks.report("n1", "web", "http", HealthChecks.Status.PASSING);
        checks.report("n1", "web", "script", HealthChecks.Status.WARNING);
        checks.report("n2", "web", "http", HealthChecks.Status.CRITICAL);
        assertEquals(HealthChecks.Status.WARNING, checks.instanceStatus("n1", "web"), "实例取最差");
        assertEquals(HealthChecks.Status.CRITICAL, checks.aggregate(List.of("n1", "n2"), "web"),
                "服务聚合取最差");
        assertEquals(HealthChecks.Status.PASSING, checks.instanceStatus("n3", "web"), "无检查视为 passing");
        assertEquals(1, checks.markNodeDown("n2"), "节点下线全检查 critical");
        assertEquals(HealthChecks.Status.CRITICAL, checks.instanceStatus("n2", "web"));
        assertThrows(IllegalArgumentException.class, () -> checks.aggregate(List.of(), "web"));
    }

    @Test
    void blockingQueries() {
        List<String> state = new ArrayList<>(List.of("a"));
        BlockingQueries blocking = new BlockingQueries(() -> new ArrayList<>(state));
        assertEquals(1, blocking.index());
        assertNull(blocking.poll("w1", 1), "无变更阻塞让出");
        state.add("b");
        assertEquals(List.of("w1"), blocking.change(), "变更唤醒一批等待者");
        assertEquals("a,b", blocking.take("w1"), "唤醒快照一致性");
        assertNull(blocking.take("w1"), "唤醒结果一次性取");
        assertEquals("a,b", blocking.poll("w2", 1), "index 已推进立即返回");
        assertTrue(blocking.change().isEmpty(), "无等待者空唤醒");
        assertEquals(3, blocking.index(), "index 单调递增");
        assertThrows(IllegalArgumentException.class, () -> blocking.poll(" ", 1));
    }

    @Test
    void preparedQueryFailover() {
        PreparedQueries queries = new PreparedQueries();
        queries.define("web-primary", "web", "");
        assertThrows(IllegalArgumentException.class, () -> queries.define("web-primary", "web", ""),
                "查询重名拒绝");
        List<PreparedQueries.Candidate> candidates = List.of(
                new PreparedQueries.Candidate("near", List.of("primary"), true),
                new PreparedQueries.Candidate("far", List.of("primary"), true),
                new PreparedQueries.Candidate("down", List.of(), false));
        assertEquals(List.of("near", "far"), queries.resolve("web-primary", candidates), "就近健康序");
        assertEquals(List.of("near"), queries.execute("web-primary", candidates, node -> true),
                "首选成功");
        assertEquals(List.of("near", "far"),
                queries.execute("web-primary", candidates, node -> node.equals("far")),
                "失败重选下一");
        assertThrows(IllegalStateException.class,
                () -> queries.execute("web-primary", candidates, node -> false), "全部失败整体失败");
        queries.define("tagged", "web", "backup");
        assertTrue(queries.resolve("tagged", candidates).isEmpty(), "过滤条件不匹配为空");
        assertTrue(queries.execute("tagged", candidates, node -> true).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> queries.resolve("nope", candidates));
    }

    @Test
    void sessionLifecycle() {
        Sessions sessions = new Sessions();
        List<String> released = new ArrayList<>();
        Sessions.Session first = sessions.create("n1", 5, 0);
        assertEquals("sess-1", first.id());
        assertTrue(sessions.isValid(first.id()));
        sessions.renew(first.id(), 10);
        assertTrue(sessions.tick(15).isEmpty(), "续期后端点未过期");
        sessions.hold(first.id(), () -> released.add("lock-a"));
        sessions.hold(first.id(), () -> released.add("lock-b"));
        assertEquals(List.of(first.id()), sessions.tick(16), "到期失效");
        assertEquals(List.of("lock-a", "lock-b"), released, "失效释放全部持有物");
        assertThrows(IllegalStateException.class, () -> sessions.invalidate(first.id()), "重复失效拒绝");
        assertThrows(IllegalStateException.class, () -> sessions.hold(first.id(), () -> {
        }), "失效会话登记拒绝");
        assertThrows(IllegalArgumentException.class, () -> sessions.renew("nope", 1));
        assertThrows(IllegalArgumentException.class, () -> sessions.create("n2", 0, 0));
    }

    @Test
    void locksAndSemaphores() {
        Locks locks = new Locks();
        assertTrue(locks.lock("leader", "s1"));
        assertTrue(locks.lock("leader", "s1"), "自持幂等");
        assertFalse(locks.lock("leader", "s2"), "单持有者互斥排队");
        assertEquals("s2", locks.releaseLock("leader", "s1"), "释放后等待者依序接管");
        assertTrue(locks.semaphore("pool", "s1", 2));
        assertTrue(locks.semaphore("pool", "s2", 2));
        assertFalse(locks.semaphore("pool", "s3", 2), "N 槽并发上限");
        assertEquals("s3", locks.releaseSemaphore("pool", "s1"), "槽释放队首接管");
        assertEquals(Set.of("s2", "s3"), locks.semaphoreHolders("pool"));

        locks.lock("leader", "s1");
        locks.semaphore("pool", "s4", 2);
        List<String> promoted = locks.onSessionInvalid("s2");
        assertEquals(List.of("lock:leader:s1", "sem:pool:s4"), promoted, "会话失效自动释放与接管");
        assertEquals("s1", locks.lockHolder("leader"));
        assertEquals(Set.of("s3", "s4"), locks.semaphoreHolders("pool"));
        assertThrows(IllegalArgumentException.class, () -> locks.releaseLock("ghost", "s1"));
        assertThrows(IllegalArgumentException.class, () -> locks.releaseSemaphore("pool", "s9"));
        assertThrows(IllegalArgumentException.class, () -> locks.semaphore("x", "s1", 0));
    }

    @Test
    void antiEntropyConverge() {
        Catalogs catalogs = new Catalogs();
        catalogs.register("n1", "web");
        catalogs.register("n2", "stale");
        List<String> diff = AntiEntropies.converge(catalogs, List.of("n1/web", "n3/api"));
        assertEquals(List.of("+n3/api", "-n2/stale"), diff, "差异补齐收敛（缺增多删）");
        assertTrue(AntiEntropies.converge(catalogs, List.of("n1/web", "n3/api")).isEmpty(), "收敛幂等");
        assertEquals(List.of("n1/web", "n3/api"), catalogs.snapshot());
    }

    @Test
    void catalogPortPipeline() {
        CatalogPort port = CatalogPort.inMemory(0);
        assertTrue(port.register("n1", "web"));
        assertTrue(port.register("n2", "web"));
        assertFalse(port.register("n1", "web"), "幂等注册不再变更");
        assertEquals(3, port.index());

        assertEquals(-1, port.watch("watcher", port.index()), "无变更阻塞让出");
        assertTrue(port.register("n3", "web"));
        assertEquals("n1/web,n2/web,n3/web", port.takeWaiter("watcher"), "变更唤醒快照一致");
        assertTrue(port.notifyChange().isEmpty(), "无等待者空唤醒");

        port.reportCheck("n1", "web", "http", "PASSING");
        port.reportCheck("n2", "web", "http", "CRITICAL");
        assertEquals("CRITICAL", port.health("web"), "服务聚合取最差");
        assertThrows(IllegalArgumentException.class,
                () -> port.reportCheck("n1", "web", "x", "BAD"), "非法状态拒绝");

        port.prepare("web-pq", "web", "");
        assertEquals(List.of("n1"), port.executeQuery("web-pq", ""), "健康就近首选");
        assertEquals(List.of("n1", "n3"), port.executeQuery("web-pq", "n1"), "失败重选下一");

        String holder = port.session("n1", 5);
        String waiter = port.session("n2", 50);
        assertEquals("ACQUIRED", port.lock("leader", holder));
        assertEquals("QUEUED", port.lock("leader", waiter));
        assertEquals("ACQUIRED", port.semaphore("pool", holder, 2));
        assertEquals("ACQUIRED", port.semaphore("pool", waiter, 2));
        for (int index = 0; index < 4; index++) {
            port.tick();
        }
        port.renew(holder);
        for (int index = 0; index < 5; index++) {
            port.tick();
        }
        assertEquals(holder, port.lockHolder("leader"), "续期后端点未失效");
        port.tick();
        assertEquals(waiter, port.lockHolder("leader"), "会话失效自动释放等待者接管");

        long indexBefore = port.index();
        port.converge(List.of("n1/web", "n2/web", "n3/web", "n4/api"));
        assertTrue(port.converge(List.of("n1/web", "n2/web", "n3/web", "n4/api")).isEmpty(), "收敛幂等");
        assertEquals(indexBefore, port.index(), "收敛不触发 watch 通知");
        assertEquals(1, port.deregisterNode("n3"));
        assertEquals(List.of("n1", "n2"), port.nodes("web"));
        assertEquals(List.of("service", "name", "traceId", "spanId"), port.topologyShape(),
                "tracekernel 服务拓扑形状只读联动");
    }
}
