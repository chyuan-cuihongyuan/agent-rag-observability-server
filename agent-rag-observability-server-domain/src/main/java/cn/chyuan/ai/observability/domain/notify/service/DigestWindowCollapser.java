package cn.chyuan.ai.observability.domain.notify.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * digest 摘要折叠（工单 0296 AL4，借鉴 Novu digest/Grafana 折叠）—
 * 时间窗内同事件类型+同指纹通知折叠为一条摘要（条数/首末时间/最高严重级归并）；
 * 指纹 = 事件类型 + 实体 URN 归一化。纯函数。
 */
public final class DigestWindowCollapser {

    /** 待折叠通知最小载体 */
    public record CollapsibleItem(String id, String eventType, String severity, String fingerprint, long atMs) {
    }

    /** 折叠摘要行 */
    public record DigestLine(String eventType, String fingerprint, int count, long firstAtMs, long lastAtMs,
            String highestSeverity) {
    }

    private DigestWindowCollapser() {
    }

    /** 窗口内折叠（同键分组归并：条数/首末/最高严重级）；单条直通仍产出一条摘要行 count=1 */
    public static List<DigestLine> collapse(List<CollapsibleItem> items) {
        Map<String, DigestLine> grouped = new LinkedHashMap<>();
        for (CollapsibleItem item : items) {
            String key = item.eventType() + "|" + item.fingerprint();
            DigestLine existing = grouped.get(key);
            if (existing == null) {
                grouped.put(key, new DigestLine(item.eventType(), item.fingerprint(), 1,
                        item.atMs(), item.atMs(), item.severity()));
                continue;
            }
            grouped.put(key, merge(existing, item));
        }
        List<DigestLine> lines = new ArrayList<>(grouped.values());
        lines.sort((a, b) -> Long.compare(a.firstAtMs(), b.firstAtMs()));
        return lines;
    }

    /** 严重级归并取最高 */
    static DigestLine merge(DigestLine line, CollapsibleItem item) {
        String highest = severityRank(item.severity()) > severityRank(line.highestSeverity())
                ? item.severity() : line.highestSeverity();
        return new DigestLine(line.eventType(), line.fingerprint(), line.count() + 1,
                Math.min(line.firstAtMs(), item.atMs()), Math.max(line.lastAtMs(), item.atMs()), highest);
    }

    static int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 2;
            case "WARNING" -> 1;
            default -> 0;
        };
    }

    /** 通知幂等指纹（事件类型 + 实体 URN 归一化：小写去空白） */
    public static String fingerprint(String eventType, String entityUrn) {
        return (eventType + "|" + (entityUrn == null ? "-" : entityUrn.trim().toLowerCase())).toLowerCase();
    }
}
