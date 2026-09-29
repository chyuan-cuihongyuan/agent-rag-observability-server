package cn.chyuan.ai.observability.domain.barrierkernel.service;

import java.util.ArrayList;
import java.util.List;

import cn.chyuan.ai.observability.domain.barrierkernel.service.Barriers.Barrier;
import cn.chyuan.ai.observability.domain.barrierkernel.service.Barriers.Injector;
import cn.chyuan.ai.observability.domain.barrierkernel.service.Barriers.Seen;
import cn.chyuan.ai.observability.domain.barrierkernel.service.CheckpointMode.Mode;

/**
 * 检查点协调器（工单 1052 EN8，flink 思想）。
 * 组合注入/对齐/ack/超时/快照/恢复/模式；
 * pipekernel 事件流形态只读联动：事件形状串（形状数据不 import pipekernel）。
 */
public final class CheckpointCoordinator implements BarrierPort {

    private static final List<String> OPERATORS = List.of("source", "align", "sink");

    private int channels = -1;
    private Injector injector;
    private long timeoutTicks;
    private CheckpointMode mode;
    private final Seen seen = new Seen();
    private ChannelAligner aligner;
    private final CheckpointAcks acks = new CheckpointAcks();
    private final AlignTimeouts timeouts = new AlignTimeouts();
    private final StateSnapshots snapshots = new StateSnapshots();
    private final Restorer restorer = new Restorer();
    private long now;
    private long aligningId = -1;

    @Override
    public synchronized void configure(int channels, long intervalTicks, long timeoutTicks, Mode mode) {
        if (this.channels > 0) {
            throw new IllegalStateException("已装配，重复配置拒绝");
        }
        this.channels = channels;
        this.injector = new Injector(intervalTicks, intervalTicks);
        this.timeoutTicks = timeoutTicks;
        this.mode = new CheckpointMode(mode);
        this.aligner = new ChannelAligner(channels);
    }

    private synchronized void requireConfigured() {
        if (channels <= 0) {
            throw new IllegalStateException("未装配拓扑");
        }
    }

    @Override
    public synchronized void putState(String operator, String key, Object value) {
        snapshots.put(operator, key, value);
    }

    @Override
    public synchronized List<Barrier> tick() {
        requireConfigured();
        now++;
        List<Barrier> injected = new ArrayList<>();
        Barrier barrier = injector.maybeInject(now);
        if (barrier != null) {
            injected.add(barrier);
        }
        if (aligningId > 0 && timeouts.expired(aligningId, now)) {
            aligner.abort();
            acks.abort(aligningId);
            timeouts.clear(aligningId);
            aligningId = -1;
        }
        return injected;
    }

    @Override
    public synchronized boolean onBarrier(int channel, long checkpointId) {
        requireConfigured();
        if (aligningId > 0) {
            if (checkpointId != aligningId) {
                throw new IllegalStateException("对齐中收到异 id: " + checkpointId);
            }
        } else {
            seen.accept(checkpointId);
            aligningId = checkpointId;
            timeouts.start(checkpointId, now, timeoutTicks);
        }
        boolean aligned = aligner.onBarrier(channel, checkpointId);
        if (aligned) {
            snapshots.snapshot(checkpointId);
            acks.start(checkpointId, OPERATORS);
        }
        return aligned;
    }

    @Override
    public synchronized boolean onData(int channel, String item) {
        requireConfigured();
        if (mode.requiresAlignment()) {
            return aligner.onData(channel, item);
        }
        return mode.passThrough(item);
    }

    @Override
    public synchronized List<String> releaseBuffered() {
        requireConfigured();
        List<String> drained = aligner.release();
        if (aligningId > 0) {
            timeouts.clear(aligningId);
            aligningId = -1;
        }
        return drained;
    }

    @Override
    public synchronized boolean ack(long checkpointId, String operator) {
        requireConfigured();
        boolean complete = acks.ack(checkpointId, operator);
        if (complete) {
            restorer.register(checkpointId);
            acks.finish(checkpointId);
        }
        return complete;
    }

    @Override
    public synchronized boolean completed(long checkpointId) {
        return restorer.hasSnapshot() && restorer.latest() >= checkpointId;
    }

    @Override
    public synchronized long restore() {
        requireConfigured();
        long latest = restorer.latest();
        snapshots.restore(latest);
        return latest;
    }

    @Override
    public synchronized long nextCheckpointId() {
        return restorer.nextCheckpointId();
    }

    @Override
    public synchronized int buffered() {
        return aligner == null ? 0 : aligner.bufferedCount();
    }

    @Override
    public String eventShape(String item) {
        if (item == null || item.isEmpty()) {
            throw new IllegalArgumentException("事件为空");
        }
        return "log{message=\"" + item + "\"}";
    }
}
