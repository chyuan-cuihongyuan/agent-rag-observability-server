package cn.chyuan.ai.observability.domain.logkernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 块存储与裁剪（工单 0578 BQ6，loki chunk 思想）。
 * 按流+时间窗口分块/块最小最大时间戳索引/查询时间范围块裁剪/
 * 块行数字节数统计/空块不建。进程内内存态（分布式存储出界）。
 */
public final class BlockStore {

    /** 单块：流内某时间窗的行集 */
    public static final class Block {
        private final String streamKey;
        private final long windowStart;
        private final List<LogStreams.Entry> entries = new ArrayList<>();
        private long minTs = Long.MAX_VALUE;
        private long maxTs = Long.MIN_VALUE;
        private long bytes;

        Block(String streamKey, long windowStart) {
            this.streamKey = streamKey;
            this.windowStart = windowStart;
        }

        void append(long ts, String line) {
            entries.add(new LogStreams.Entry(ts, line));
            minTs = Math.min(minTs, ts);
            maxTs = Math.max(maxTs, ts);
            bytes += line.length();
        }

        public String streamKey() {
            return streamKey;
        }

        public long windowStart() {
            return windowStart;
        }

        public long minTs() {
            return minTs;
        }

        public long maxTs() {
            return maxTs;
        }

        public int lineCount() {
            return entries.size();
        }

        public long byteCount() {
            return bytes;
        }

        public List<LogStreams.Entry> entries() {
            return List.copyOf(entries);
        }

        /** 块与查询范围 [from,to] 是否相交（块索引裁剪依据） */
        boolean overlaps(long from, long to) {
            return minTs <= to && maxTs >= from;
        }
    }

    private final long blockWindowMillis;
    private final TreeMap<String, TreeMap<Long, Block>> byStream = new TreeMap<>();

    public BlockStore(long blockWindowMillis) {
        if (blockWindowMillis <= 0) {
            throw new IllegalArgumentException("块时间窗须为正");
        }
        this.blockWindowMillis = blockWindowMillis;
    }

    /** 追加行（按流+窗口定位块；窗口对齐 floorDiv） */
    public void append(String streamKey, long ts, String line) {
        long window = Math.floorDiv(ts, blockWindowMillis) * blockWindowMillis;
        byStream.computeIfAbsent(streamKey, k -> new TreeMap<>())
                .computeIfAbsent(window, w -> new Block(streamKey, window))
                .append(ts, line);
    }

    public int streamCount() {
        return byStream.size();
    }

    /** 块总数 */
    public int blockCount() {
        int total = 0;
        for (TreeMap<Long, Block> blocks : byStream.values()) {
            total += blocks.size();
        }
        return total;
    }

    /** 范围查询：按块索引裁剪后归并命中行（升序；空结果空表） */
    public List<LogStreams.Entry> query(String streamKey, long from, long to) {
        if (from > to) {
            throw new IllegalArgumentException("查询范围非法: from>to");
        }
        TreeMap<Long, Block> blocks = byStream.get(streamKey);
        List<LogStreams.Entry> out = new ArrayList<>();
        if (blocks == null) {
            return out;
        }
        for (Block block : blocks.values()) {
            if (block.overlaps(from, to)) {
                for (LogStreams.Entry entry : block.entries()) {
                    if (entry.ts() >= from && entry.ts() <= to) {
                        out.add(entry);
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    /** 流统计：总行数与字节数 */
    public long[] streamStats(String streamKey) {
        TreeMap<Long, Block> blocks = byStream.get(streamKey);
        if (blocks == null) {
            return new long[]{0, 0};
        }
        long lines = 0;
        long bytes = 0;
        for (Block block : blocks.values()) {
            lines += block.lineCount();
            bytes += block.byteCount();
        }
        return new long[]{lines, bytes};
    }
}
