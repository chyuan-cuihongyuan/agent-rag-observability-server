package cn.chyuan.ai.observability.domain.barrierkernel.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通道对齐（工单 1046 EN2，flink barrier 对齐思想）。
 * 多通道 barrier 齐才触发/先到入对齐缓冲/未齐不快照。
 */
public final class ChannelAligner {

    private final int channels;
    private final Map<Integer, Boolean> marked = new HashMap<>();
    private final Map<Integer, Deque<String>> buffers = new HashMap<>();
    private long alignedId = -1;

    public ChannelAligner(int channels) {
        if (channels <= 0) {
            throw new IllegalArgumentException("通道数须为正: " + channels);
        }
        this.channels = channels;
    }

    public int channels() {
        return channels;
    }

    /** 数据到达：该通道 barrier 已到则入对齐缓冲，返回 true 表示被缓冲 */
    public synchronized boolean onData(int channel, String item) {
        requireChannel(channel);
        if (marked.getOrDefault(channel, false)) {
            buffers.computeIfAbsent(channel, k -> new ArrayDeque<>()).addLast(item);
            return true;
        }
        return false;
    }

    /** barrier 到达：全部通道齐返回 true（触发快照）；未齐不快照 */
    public synchronized boolean onBarrier(int channel, long checkpointId) {
        requireChannel(channel);
        marked.put(channel, true);
        boolean aligned = marked.size() == channels
                && marked.values().stream().allMatch(Boolean::booleanValue);
        if (aligned) {
            alignedId = checkpointId;
        }
        return aligned;
    }

    /** 释放对齐缓冲（快照落定后放行），并复位对齐标记进入下一轮 */
    public synchronized List<String> release() {
        List<String> drained = new ArrayList<>();
        buffers.values().forEach(queue -> drained.addAll(queue));
        buffers.clear();
        marked.clear();
        alignedId = -1;
        return drained;
    }

    /** 已缓冲数据量（失败释放亦走此清空） */
    public synchronized int bufferedCount() {
        return buffers.values().stream().mapToInt(Deque::size).sum();
    }

    /** 对齐失败：释放缓冲并复位 */
    public synchronized void abort() {
        release();
    }

    public synchronized long alignedId() {
        return alignedId;
    }

    private void requireChannel(int channel) {
        if (channel < 0 || channel >= channels) {
            throw new IllegalArgumentException("通道越界: " + channel + "/" + channels);
        }
    }
}
