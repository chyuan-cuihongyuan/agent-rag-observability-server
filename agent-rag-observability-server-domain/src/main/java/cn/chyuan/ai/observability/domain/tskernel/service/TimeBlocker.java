package cn.chyuan.ai.observability.domain.tskernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间分块（工单 0481 BF2，influxdb TSMProm 思想）。
 * 按时间窗口切块/块索引（最小最大时间戳）/范围查询块裁剪。
 */
public class TimeBlocker {

    /** 时序样本 */
    public record Sample(long timestamp, double value) {
    }

    /** 数据块：窗口内样本（升序） */
    public record Block(long windowStart, long windowEnd, List<Sample> samples) {
        public long minTimestamp() {
            return samples.get(0).timestamp();
        }

        public long maxTimestamp() {
            return samples.get(samples.size() - 1).timestamp();
        }
    }

    private final long windowMillis;

    public TimeBlocker(long windowMillis) {
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("窗口须 > 0: " + windowMillis);
        }
        this.windowMillis = windowMillis;
    }

    /** 切块：样本按 timestamp 升序落入 [start, start+window) 窗口 */
    public List<Block> chunk(List<Sample> samples) {
        List<Sample> sorted = new ArrayList<>(samples);
        sorted.sort(java.util.Comparator.comparingLong(Sample::timestamp));
        List<Block> blocks = new ArrayList<>();
        List<Sample> current = new ArrayList<>();
        long currentWindow = Long.MIN_VALUE;
        for (Sample sample : sorted) {
            long window = Math.floorDiv(sample.timestamp(), windowMillis) * windowMillis;
            if (window != currentWindow && !current.isEmpty()) {
                blocks.add(new Block(currentWindow, currentWindow + windowMillis, List.copyOf(current)));
                current = new ArrayList<>();
            }
            currentWindow = window;
            current.add(sample);
        }
        if (!current.isEmpty()) {
            blocks.add(new Block(currentWindow, currentWindow + windowMillis, List.copyOf(current)));
        }
        return blocks;
    }

    /** 范围裁剪：仅返回与 [from, to] 有交集的块（块索引 min/max 判定） */
    public List<Block> pruneByRange(List<Block> blocks, long from, long to) {
        if (from > to) {
            throw new IllegalArgumentException("范围非法 from > to");
        }
        List<Block> matched = new ArrayList<>();
        for (Block block : blocks) {
            if (block.maxTimestamp() >= from && block.minTimestamp() <= to) {
                matched.add(block);
            }
        }
        return matched;
    }

    public long windowMillis() {
        return windowMillis;
    }
}
