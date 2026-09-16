package cn.chyuan.ai.observability.domain.anomaly.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警相关性聚合（工单 0411 AX7）。
 * 告警事件流（时间戳+标签集+指纹）→ 滑动时间窗内同指纹聚类成告警组（首末时间/成员数/代表告警）
 * → 组内后续事件标记 SUPPRESSED 抑制重复；跨组关联（同实体不同规则）留痕。只读不改既有 alert 域。纯函数。
 */
public class AlertCorrelator {

    /** 告警事件入参 */
    public record AlertEvent(long timestampMs, String fingerprint, String entity, String rule, String message) {
    }

    /** 告警组 */
    public record AlertGroup(String fingerprint, long firstMs, long lastMs, int memberCount,
                             AlertEvent representative, List<AlertEvent> members) {
    }

    /** 聚合结果：组 + 被抑制事件 + 跨组关联对 */
    public record Correlation(List<AlertGroup> groups, List<AlertEvent> suppressed, List<String> crossLinks) {
    }

    private final long windowMs;

    public AlertCorrelator(long windowMs) {
        if (windowMs <= 0) {
            throw new IllegalArgumentException("时间窗必须为正");
        }
        this.windowMs = windowMs;
    }

    /**
     * 聚合：事件按（时间）排序、按指纹分段滑窗——同指纹连续事件与组首超窗则闭合旧组开启新组；
     * 组首为代表，其余 SUPPRESSED；跨组关联：同实体不同指纹组对（去重）。
     */
    public Correlation correlate(List<AlertEvent> events) {
        List<AlertEvent> sorted = new ArrayList<>(events == null ? List.of() : events);
        sorted.sort(Comparator.comparingLong(AlertEvent::timestampMs));
        List<AlertGroup> groups = new ArrayList<>();
        List<AlertEvent> suppressed = new ArrayList<>();
        List<AlertEvent> current = null;
        String currentFingerprint = null;
        for (AlertEvent event : sorted) {
            boolean newGroup = current == null || !event.fingerprint().equals(currentFingerprint)
                    || event.timestampMs() - current.get(0).timestampMs() > windowMs;
            if (newGroup) {
                if (current != null) {
                    emitGroup(current, groups, suppressed);
                }
                current = new ArrayList<>();
                currentFingerprint = event.fingerprint();
            }
            current.add(event);
        }
        if (current != null) {
            emitGroup(current, groups, suppressed);
        }
        groups.sort(Comparator.comparingLong(AlertGroup::firstMs));
        // 跨组关联：同实体不同指纹（指纹对去重）
        List<String> crossLinks = new ArrayList<>();
        java.util.Set<String> linkedPairs = new java.util.LinkedHashSet<>();
        for (int i = 0; i < groups.size(); i++) {
            for (int j = i + 1; j < groups.size(); j++) {
                AlertGroup a = groups.get(i);
                AlertGroup b = groups.get(j);
                if (a.representative().entity().equals(b.representative().entity())
                        && !a.fingerprint().equals(b.fingerprint())) {
                    String pair = a.fingerprint().compareTo(b.fingerprint()) < 0
                            ? a.fingerprint() + "<->" + b.fingerprint()
                            : b.fingerprint() + "<->" + a.fingerprint();
                    if (linkedPairs.add(pair)) {
                        crossLinks.add(pair + "@" + a.representative().entity());
                    }
                }
            }
        }
        return new Correlation(groups, suppressed, crossLinks);
    }

    /** 组装配：首事件为代表，其余抑制 */
    private void emitGroup(List<AlertEvent> members, List<AlertGroup> groups, List<AlertEvent> suppressed) {
        AlertEvent representative = members.get(0);
        groups.add(new AlertGroup(representative.fingerprint(), representative.timestampMs(),
                members.get(members.size() - 1).timestampMs(), members.size(), representative, members));
        for (int i = 1; i < members.size(); i++) {
            suppressed.add(members.get(i));
        }
    }
}
