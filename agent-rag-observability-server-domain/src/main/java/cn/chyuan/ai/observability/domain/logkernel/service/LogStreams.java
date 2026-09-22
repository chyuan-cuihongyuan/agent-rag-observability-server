package cn.chyuan.ai.observability.domain.logkernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 日志流模型（工单 0573 BQ1，loki 标签流思想）。
 * 标签集（排序后键值串）划分流/流指纹 FNV-1a 64 稳定分配/流内行追加有序/
 * 标签基数与流数统计/空标签默认流。log-kernel.enabled 默认关。
 */
public final class LogStreams {

    /** 空标签默认流键 */
    public static final String DEFAULT_STREAM = "{}";

    private LogStreams() {
    }

    /** 流键：标签按 key 字典序 k=v 逗号连接（空标签为 {}） */
    public static String streamKey(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return DEFAULT_STREAM;
        }
        StringBuilder sb = new StringBuilder("{");
        new TreeMap<>(labels).forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append(k).append('=').append(v);
        });
        return sb.append('}').toString();
    }

    /** FNV-1a 64 位流指纹（稳定分配） */
    public static long fingerprint(String streamKey) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < streamKey.length(); i++) {
            hash ^= streamKey.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /** 流内条目（追加有序） */
    public record Entry(long ts, String line) {
    }

    /** 单流：指纹 + 条目集 + 字节统计 */
    public static final class Stream {
        private final String key;
        private final long fingerprint;
        private final List<Entry> entries = new ArrayList<>();
        private long bytes;

        public Stream(String key) {
            this.key = key;
            this.fingerprint = LogStreams.fingerprint(key);
        }

        /** 追加行（时间戳非负；行非空） */
        public void append(long ts, String line) {
            if (ts < 0) {
                throw new IllegalArgumentException("时间戳须非负");
            }
            if (line == null || line.isEmpty()) {
                throw new IllegalArgumentException("日志行不得为空");
            }
            entries.add(new Entry(ts, line));
            bytes += line.length();
        }

        public String key() {
            return key;
        }

        public long fingerprint() {
            return fingerprint;
        }

        public List<Entry> entries() {
            return List.copyOf(entries);
        }

        public int lineCount() {
            return entries.size();
        }

        public long byteCount() {
            return bytes;
        }
    }

    /** 流集：按流键组织，键空标签落默认流 */
    public static final class StreamSet {
        private final TreeMap<String, Stream> streams = new TreeMap<>();

        public Stream ingest(Map<String, String> labels, long ts, String line) {
            String key = streamKey(labels);
            Stream stream = streams.computeIfAbsent(key, Stream::new);
            stream.append(ts, line);
            return stream;
        }

        public Stream stream(String key) {
            return streams.get(key);
        }

        /** 流键清单（字典序） */
        public List<String> keys() {
            return List.copyOf(streams.keySet());
        }

        public int streamCount() {
            return streams.size();
        }

        /** 标签基数：指定标签键的不同取值数 */
        public long cardinality(String labelKey) {
            long distinct = streams.keySet().stream()
                    .filter(k -> k.contains("," + labelKey + "=") || k.startsWith("{" + labelKey + "="))
                    .count();
            return distinct;
        }

        public long totalLines() {
            return streams.values().stream().mapToLong(Stream::lineCount).sum();
        }
    }
}
