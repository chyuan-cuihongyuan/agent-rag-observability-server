package cn.chyuan.ai.observability.domain.barrierkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import cn.chyuan.ai.observability.domain.barrierkernel.service.Barriers.Barrier;
import cn.chyuan.ai.observability.domain.barrierkernel.service.CheckpointMode.Mode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 检查点屏障内核测试（工单 1045-1052 EN1-EN8，flink 思想）。
 * 屏障注入/通道对齐/快照 ack/对齐超时/状态快照/恢复/模式语义/端口组合管线。
 */
class BarrierKernelTest {

    @Test
    void barrierInject() {
        Barriers.Injector injector = new Barriers.Injector(3, 3);
        assertNull(injector.maybeInject(1), "未到点不注入");
        assertNull(injector.maybeInject(2));
        Barrier first = injector.maybeInject(3);
        assertEquals(1, first.checkpointId(), "周期注入携带 checkpointId");
        assertEquals("source-1", first.source());
        assertNull(injector.maybeInject(4));
        assertEquals(2, injector.maybeInject(6).checkpointId(), "id 单调递增");

        Barriers.Seen seen = new Barriers.Seen();
        seen.accept(1);
        assertThrows(IllegalStateException.class, () -> seen.accept(1), "重复 id 拒绝");
        seen.accept(2);
        assertEquals(2, seen.size());
        assertThrows(IllegalArgumentException.class, () -> new Barriers.Barrier(0, "s"), "非正 id 拒绝");
        assertThrows(IllegalArgumentException.class, () -> new Barriers.Barrier(1, ""), "空 source 拒绝");
        assertThrows(IllegalArgumentException.class, () -> new Barriers.Injector(0, 0), "零间隔拒绝");
    }

    @Test
    void channelAlignment() {
        ChannelAligner aligner = new ChannelAligner(2);
        assertFalse(aligner.onBarrier(0, 1), "未齐不快照");
        assertEquals(-1, aligner.alignedId());
        assertTrue(aligner.onData(0, "late-item"), "barrier 后数据入对齐缓冲");
        assertFalse(aligner.onData(1, "normal"), "未标记通道数据直通");
        assertEquals(1, aligner.bufferedCount());
        assertTrue(aligner.onBarrier(1, 1), "多通道齐触发");
        assertEquals(1, aligner.alignedId());
        assertEquals(List.of("late-item"), aligner.release(), "快照后释放缓冲");
        assertEquals(0, aligner.bufferedCount());
        assertEquals(-1, aligner.alignedId(), "复位进入下一轮");

        assertThrows(IllegalArgumentException.class, () -> aligner.onBarrier(2, 1), "通道越界拒绝");
        assertThrows(IllegalArgumentException.class, () -> new ChannelAligner(0), "零通道拒绝");
    }

    @Test
    void checkpointAcks() {
        CheckpointAcks acks = new CheckpointAcks();
        acks.start(1, List.of("source", "align", "sink"));
        assertFalse(acks.isComplete(1), "缺 ack 未完成");
        assertFalse(acks.ack(1, "source"));
        assertFalse(acks.ack(1, "align"), "部分 ack 未完成");
        acks.ack(1, "align");
        assertFalse(acks.isComplete(1), "重复 ack 幂等不提前完成");
        assertTrue(acks.ack(1, "sink"), "收齐完成");
        assertTrue(acks.isComplete(1));
        acks.finish(1);
        assertEquals(0, acks.pendingCount());
        assertThrows(IllegalArgumentException.class, () -> acks.ack(1, "source"), "窗口已关拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> acks.ack(2, "source"), "未开启 checkpoint 拒绝");
        acks.start(3, List.of("source"));
        assertThrows(IllegalArgumentException.class, () -> acks.ack(3, "ghost"), "未注册算子拒绝");
        assertThrows(IllegalArgumentException.class, () -> acks.start(4, List.of()), "空算子拒绝");
        assertThrows(IllegalStateException.class, () -> acks.finish(3), "未收齐不可 finish");
    }

    @Test
    void alignTimeout() {
        AlignTimeouts timeouts = new AlignTimeouts();
        timeouts.start(1, 0, 5);
        assertEquals(AlignTimeouts.State.WAITING, timeouts.state(1));
        assertFalse(timeouts.expired(1, 4), "未超时继续等待");
        assertTrue(timeouts.expired(1, 5), "到点判定超时");
        assertEquals(AlignTimeouts.State.FAILED, timeouts.state(1));
        assertTrue(timeouts.expired(1, 6), "失败后幂等");
        timeouts.clear(1);
        assertEquals(0, timeouts.sessions());
        assertThrows(IllegalArgumentException.class, () -> timeouts.state(1), "清理后不存在拒绝");
        assertThrows(IllegalArgumentException.class, () -> timeouts.start(2, 0, 0), "零超时拒绝");
    }

    @Test
    void stateSnapshots() {
        StateSnapshots snapshots = new StateSnapshots();
        snapshots.put("source", "offset", 100);
        snapshots.snapshot(1);
        snapshots.put("source", "offset", 200);
        snapshots.snapshot(2);
        assertEquals(100, snapshots.view(1).get("source").get("offset"), "两次快照隔离互不覆盖");
        assertEquals(200, snapshots.view(2).get("source").get("offset"));
        snapshots.put("source", "offset", 999);
        assertEquals(100, snapshots.view(1).get("source").get("offset"), "快照只读不受当前态影响");
        snapshots.restore(1);
        assertEquals(100, snapshots.get("source", "offset"), "恢复版本回填");
        assertThrows(IllegalArgumentException.class, () -> snapshots.view(9), "未知版本拒绝");
        assertThrows(IllegalArgumentException.class, () -> snapshots.restore(9), "未知版本恢复拒绝");
        assertThrows(IllegalStateException.class, () -> snapshots.snapshot(1), "重复版本拒绝");
    }

    @Test
    void restoreLatest() {
        Restorer restorer = new Restorer();
        assertThrows(IllegalStateException.class, restorer::latest, "无快照拒绝");
        restorer.register(1);
        restorer.register(5);
        assertEquals(5, restorer.latest(), "最近完成快照");
        assertEquals(6, restorer.nextCheckpointId(), "恢复后 checkpointId 递增");
        assertTrue(restorer.hasSnapshot());
        assertEquals(2, restorer.count());
        assertThrows(IllegalStateException.class, () -> restorer.register(3), "非递增拒绝");
    }

    @Test
    void checkpointModeSemantics() {
        CheckpointMode exactly = new CheckpointMode(Mode.EXACTLY_ONCE);
        assertTrue(exactly.requiresAlignment(), "exactly-once 需对齐");
        assertThrows(IllegalStateException.class, () -> exactly.passThrough("k"), "exactly-once 不直通");

        CheckpointMode atLeast = new CheckpointMode(Mode.AT_LEAST_ONCE);
        assertFalse(atLeast.requiresAlignment(), "at-least-once 直通不对齐");
        assertFalse(atLeast.passThrough("evt-1"), "首投递非重复");
        assertTrue(atLeast.passThrough("evt-1"), "重复投递标记去重");
        assertEquals(1, atLeast.marks());
        atLeast.resetMarks();
        assertFalse(atLeast.passThrough("evt-1"), "新窗口重置去重标记");
        assertThrows(IllegalArgumentException.class, () -> new CheckpointMode(null), "空模式拒绝");
    }

    @Test
    void barrierPortPipeline() {
        BarrierPort port = BarrierPort.inMemory();
        assertThrows(IllegalStateException.class, () -> port.tick(), "未装配拒绝");
        port.configure(2, 2, 10, Mode.EXACTLY_ONCE);
        assertThrows(IllegalStateException.class,
                () -> port.configure(2, 2, 10, Mode.EXACTLY_ONCE), "重复装配拒绝");

        port.putState("source", "offset", 10);
        assertTrue(port.tick().isEmpty(), "未到点不注入");
        List<Barrier> injected = port.tick();
        assertEquals(1, injected.size(), "到点注入 barrier");
        long cid = injected.get(0).checkpointId();

        assertFalse(port.onData(1, "normal"), "未标记通道直通");
        assertFalse(port.onBarrier(0, cid), "单通道未齐");
        assertTrue(port.onData(0, "after-barrier"), "该通道 barrier 后数据入对齐缓冲");
        assertEquals(1, port.buffered());
        assertTrue(port.onBarrier(1, cid), "两通道齐触发快照");
        assertThrows(IllegalStateException.class, () -> port.onBarrier(0, cid), "重复 id 拒绝");

        assertFalse(port.ack(cid, "source"));
        assertFalse(port.ack(cid, "align"));
        assertTrue(port.ack(cid, "sink"), "算子 ack 收齐完成");
        assertTrue(port.completed(cid));
        assertEquals(List.of("after-barrier"), port.releaseBuffered(), "快照落定释放缓冲");

        port.putState("source", "offset", 99);
        assertEquals(cid, port.restore(), "恢复最近完成快照");
        assertEquals(cid + 1, port.nextCheckpointId(), "恢复后 id 递增");

        assertEquals("log{message=\"evt\"}", port.eventShape("evt"), "pipekernel 事件形状联动");
        assertThrows(IllegalArgumentException.class, () -> port.eventShape(""), "空事件拒绝");
    }

    @Test
    void atLeastOncePipeline() {
        BarrierPort port = BarrierPort.inMemory();
        port.configure(2, 1, 5, Mode.AT_LEAST_ONCE);
        List<Barrier> injected = port.tick();
        long cid = injected.get(0).checkpointId();
        assertFalse(port.onData(0, "evt-1"), "at-least-once 直通不缓冲");
        assertTrue(port.onData(0, "evt-1"), "重复投递直通并标记去重");
        assertFalse(port.onBarrier(0, cid));
        assertTrue(port.onBarrier(1, cid), "不对齐模式仍收齐触发快照");
        port.ack(cid, "source");
        port.ack(cid, "align");
        assertTrue(port.ack(cid, "sink"));
        assertEquals(0, port.buffered(), "直通零缓冲");
    }
}
