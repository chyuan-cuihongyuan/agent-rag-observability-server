package cn.chyuan.ai.observability.domain.logkernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 日志流登记（工单 0579 BQ7，第 35 表 log_stream 的内核镜像面）。
 * 流指纹唯一键（同指纹合并统计）/标签 JSON 文本/行数字节数/首末时间戳/
 * 状态 ACTIVE-DELETED。log-kernel.enabled 默认关。
 */
public final class LogStreamRegistry {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    /** 流登记行（fingerprint 唯一键） */
    public record LogStreamRow(long fingerprint, String streamKey, long lineCount, long byteCount,
            long firstTs, long lastTs, String status) {
    }

    private final Map<Long, LogStreamRow> rows = new TreeMap<>();

    /** 摄入登记：同指纹流合并行数字节数并推进首末时间戳 */
    public void ingest(String streamKey, int lines, long bytes, long firstTs, long lastTs) {
        if (streamKey == null || streamKey.isEmpty()) {
            throw new IllegalArgumentException("流键不得为空");
        }
        long fingerprint = LogStreams.fingerprint(streamKey);
        LogStreamRow existing = rows.get(fingerprint);
        if (existing == null) {
            rows.put(fingerprint, new LogStreamRow(fingerprint, streamKey, lines, bytes,
                    firstTs, lastTs, STATUS_ACTIVE));
            return;
        }
        if (STATUS_DELETED.equals(existing.status())) {
            throw new IllegalArgumentException("已注销流拒绝摄入: " + streamKey);
        }
        rows.put(fingerprint, new LogStreamRow(fingerprint, streamKey,
                existing.lineCount() + lines, existing.byteCount() + bytes,
                Math.min(existing.firstTs(), firstTs), Math.max(existing.lastTs(), lastTs),
                STATUS_ACTIVE));
    }

    /** 注销流（墓碑；不存在拒绝） */
    public void delete(String streamKey) {
        long fingerprint = LogStreams.fingerprint(streamKey);
        LogStreamRow existing = rows.get(fingerprint);
        if (existing == null) {
            throw new IllegalArgumentException("流不存在: " + streamKey);
        }
        rows.put(fingerprint, new LogStreamRow(existing.fingerprint(), existing.streamKey(),
                existing.lineCount(), existing.byteCount(), existing.firstTs(), existing.lastTs(),
                STATUS_DELETED));
    }

    public boolean contains(String streamKey) {
        LogStreamRow row = rows.get(LogStreams.fingerprint(streamKey));
        return row != null && STATUS_ACTIVE.equals(row.status());
    }

    /** 全量快照（指纹升序） */
    public List<LogStreamRow> snapshot() {
        return List.copyOf(rows.values());
    }

    /** 行数字节统计（活跃流合计） */
    public long[] totals() {
        long lines = 0;
        long bytes = 0;
        for (LogStreamRow row : rows.values()) {
            if (STATUS_ACTIVE.equals(row.status())) {
                lines += row.lineCount();
                bytes += row.byteCount();
            }
        }
        return new long[]{lines, bytes};
    }
}
