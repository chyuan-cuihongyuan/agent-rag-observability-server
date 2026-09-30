package cn.chyuan.ai.observability.domain.loadkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 压测编排内核测试（工单 1143-1150 EY1-EY8，k6 思想）。
 * 场景定义/VU 生命周期/ramping 阶段/到达率/阈值判定/check/指标聚合/端口组合管线。
 */
class LoadKernelTest {

    @Test
    void scenarioDefs() {
        LoadPort port = LoadPort.inMemory();
        port.scenario("browse", "constant-vus").vus(2).duration(10);
        port.scenario("spike", "ramping-vus").duration(20);
        assertEquals(List.of("browse", "spike"), port.scenarios(), "多场景并存");
        assertThrows(IllegalArgumentException.class, () -> port.scenario("browse", "constant-vus"), "名称唯一重复拒绝");
        assertThrows(IllegalArgumentException.class, () -> port.scenario("x", "mystery"), "未知执行器拒绝");
        LoadPort bad = LoadPort.inMemory();
        bad.scenario("neg", "constant-vus").vus(0).duration(10);
        assertThrows(IllegalArgumentException.class, () -> bad.run("neg"), "vus 零值收口拒绝");
        LoadPort bad2 = LoadPort.inMemory();
        bad2.scenario("neg2", "constant-vus").vus(1).duration(0);
        assertThrows(IllegalArgumentException.class, () -> bad2.run("neg2"), "duration 零值收口拒绝");
        assertThrows(IllegalArgumentException.class, () -> port.run("ghost"), "未知场景拒绝");
    }

    @Test
    void vuLifecycle() {
        VirtualUsers pool = new VirtualUsers();
        pool.scaleTo(3);
        assertEquals(3, pool.activeCount(), "爬升至目标");
        int first = pool.iterate();
        assertTrue(first >= 1 && first <= 3, "迭代执行返回 VU 编号");
        pool.scaleTo(1);
        assertEquals(1, pool.activeCount(), "graceful 降容");
        pool.scaleTo(2);
        assertTrue(pool.hiredTotal() >= 4, "编号只增");
        assertTrue(pool.retiredIdsNeverReused(), "退场编号不复用");
        pool.interrupt(1);
        assertEquals(1, pool.activeCount(), "中断降容");
        VirtualUsers empty = new VirtualUsers();
        assertEquals(-1, empty.iterate(), "无活跃 VU 迭代返回 -1");
        assertThrows(IllegalArgumentException.class, () -> empty.scaleTo(-1), "负目标拒绝");
    }

    @Test
    void rampingStages() {
        RampingStages stages = new RampingStages();
        assertThrows(IllegalArgumentException.class, () -> stages.add(0, 1), "零时长阶段拒绝");
        assertThrows(IllegalArgumentException.class, () -> stages.add(5, -1), "负目标拒绝");
        stages.add(2, 1);
        stages.add(2, 3);
        stages.add(2, 1);
        assertEquals(1, stages.targetAt(0), "阶段一目标");
        assertEquals(3, stages.targetAt(2), "阶段二目标");
        assertEquals(1, stages.targetAt(5), "阶段三回落（非单调允许）");
        assertEquals(1, stages.targetAt(99), "越界取末阶段");

        VirtualUsers pool = new VirtualUsers();
        assertEquals(10, stages.totalIterations(pool, 6), "Σ 每 tick 活跃 VU：1+1+3+3+1+1");
        assertEquals(1, pool.activeCount(), "降容生效：末 tick 目标 1");
    }

    @Test
    void arrivalRate() {
        assertEquals(2, ArrivalRate.intervalTicks(5, 10), "均匀发射间隔");
        assertEquals(30, ArrivalRate.launches(5, 10, 60), "全程发射数");
        assertThrows(IllegalArgumentException.class, () -> ArrivalRate.intervalTicks(3, 10), "不整除拒绝");
        assertThrows(IllegalArgumentException.class, () -> ArrivalRate.intervalTicks(0, 10), "零 rate 拒绝");
        assertFalse(ArrivalRate.backlogged(2, 1), "迭代快于间隔无积压");
        assertTrue(ArrivalRate.backlogged(2, 3), "迭代耗时超间隔积压检出");
        ArrivalRate.admitPreAllocated(2, 2, 3);
        assertThrows(IllegalStateException.class, () -> ArrivalRate.admitPreAllocated(1, 2, 3), "preAllocatedVUs 不足拒绝");
    }

    @Test
    void thresholds() {
        assertThrows(IllegalArgumentException.class, () -> new Thresholds.Threshold("m", "rate", "=", 1), "非法算符拒绝");
        Thresholds thresholds = new Thresholds();
        thresholds.add("s", new Thresholds.Threshold("http_fail_rate", "rate", ">", 0.05));
        assertFalse(thresholds.breached("s", (m, a) -> 0.01), "未越界不 breach");
        assertTrue(thresholds.breached("s", (m, a) -> 0.10), "rate 越界 breach");
        Thresholds none = new Thresholds();
        assertFalse(none.breached("s", (m, a) -> 1.0), "无阈值不判");

        Thresholds multi = new Thresholds();
        multi.add("s2", new Thresholds.Threshold("dur", "p99", ">", 800));
        multi.add("s2", new Thresholds.Threshold("err", "rate", "<", 0.5));
        assertTrue(multi.breached("s2", (m, a) -> a.equals("p99") ? 900 : 0.9), "任一越界即场景失败");
        assertTrue(multi.breached("s2", (m, a) -> a.equals("p99") ? 100 : 0.1), "err<0.5 越界（低于下界亦 breach）");
    }

    @Test
    void checksFlow() {
        Checks checks = new Checks();
        checks.record("status is 200", "api", true);
        checks.record("status is 200", "api", true);
        checks.record("status is 200", "web", false);
        assertEquals(2.0 / 3, checks.rate("status is 200"), 1e-9, "通过率统计");
        assertEquals(1.0, checks.rateByTag("status is 200", "api"), 1e-9, "按 tag 聚合");
        assertEquals(0.0, checks.rateByTag("status is 200", "web"), 1e-9);
        assertEquals(2, checks.distinctKeys(), "检查名⊕tag 计数");
        assertEquals(0.0, checks.rate("never"), "未知 check 通过率 0");
    }

    @Test
    void metricsAggregate() {
        Metrics metrics = new Metrics();
        metrics.declare("iterations", "counter");
        metrics.declare("success", "rate");
        metrics.declare("vus", "gauge");
        metrics.declare("duration", "trend");
        metrics.sample("iterations", 1);
        metrics.sample("iterations", 1);
        metrics.sample("iterations", 1);
        assertEquals(3, metrics.aggregate("iterations", "sum"), "counter 累加");
        metrics.sample("success", 1);
        metrics.sample("success", 1);
        metrics.sample("success", 0);
        assertEquals(2.0 / 3, metrics.aggregate("success", "rate"), 1e-9, "rate 比率");
        metrics.sample("vus", 3);
        metrics.sample("vus", 7);
        assertEquals(7, metrics.aggregate("vus", "latest"), "gauge 取最新");
        for (double v : new double[]{100, 200, 300, 400, 500}) {
            metrics.sample("duration", v);
        }
        assertEquals(500, metrics.aggregate("duration", "p99"), "trend p99 分位");
        assertEquals(300, metrics.aggregate("duration", "avg"), "trend 均值");
        assertThrows(IllegalStateException.class, () -> metrics.aggregate("iterations", "p99"), "类型错用拒绝");
        assertThrows(IllegalArgumentException.class, () -> metrics.declare("iterations", "counter"), "重复指标拒绝");
        assertThrows(IllegalArgumentException.class, () -> metrics.declare("x", "histogram"), "未知类型拒绝");
        assertThrows(IllegalArgumentException.class, () -> metrics.sample("ghost", 1), "未知指标采样拒绝");
    }

    @Test
    void portPipeline() {
        LoadPort port = LoadPort.inMemory();
        port.metric("http_fail_rate", "rate");
        port.metric("dur", "trend");
        port.sample("http_fail_rate", 0);
        port.sample("http_fail_rate", 1);
        for (double v : new double[]{100, 200, 900}) {
            port.sample("dur", v);
        }
        port.scenario("soak", "constant-vus").vus(2).duration(5);
        assertEquals(10, port.run("soak"), "constant-vus 迭代数 vus×duration");
        assertEquals(2, port.vuCount("soak"));
        port.interrupt("soak", 1);
        assertEquals(1, port.vuCount("soak"), "端口中断降容");

        port.scenario("step", "ramping-vus").duration(4).stage(2, 1).stage(2, 3);
        assertEquals(8, port.run("step"), "ramping Σ 1+1+3+3");

        port.scenario("arr", "constant-arrival-rate").duration(20).rate(5).timeUnit(10).preAllocatedVus(1);
        assertEquals(10, port.run("arr"), "到达率发射数 rate×duration/timeUnit");
        assertEquals(1, port.vuCount("arr"));

        port.scenario("guarded", "constant-vus").vus(1).duration(1)
                .threshold("http_fail_rate", "rate", ">", 0.4);
        port.run("guarded");
        assertTrue(port.breached("guarded"), "阈值越界场景失败");
        port.scenario("calm", "constant-vus").vus(1).duration(1)
                .threshold("http_fail_rate", "rate", ">", 0.9);
        port.run("calm");
        assertFalse(port.breached("calm"), "未越界通过");
        port.check("status 2xx", "api", true);
        port.check("status 2xx", "api", false);
        assertEquals(0.5, port.checkRate("status 2xx"), 1e-9);
        assertEquals(List.of("timestamp", "value"), port.samplesShape(), "tskernel 样本形状只读联动");
    }
}
