package cn.chyuan.ai.observability.domain.errorkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组统计（工单 0697 CE3，sentry 思想）。
 * 组内计数与首见最近 seen/状态（unresolved/resolved）/top 问题排序
 * （计数降序+新近度平局规则）。
 */
public final class ErrorGroupStore {

    public static final String UNRESOLVED = "UNRESOLVED";
    public static final String RESOLVED = "RESOLVED";

    /** 组行：持久化口径与第 36 表对齐 */
    public static final class Group {
        private final String groupKey;
        private final String fingerprint;
        private String title;
        private long count;
        private long firstSeenMs;
        private long lastSeenMs;
        private String status = UNRESOLVED;

        Group(String groupKey, String fingerprint, String title, long atMs) {
            this.groupKey = groupKey;
            this.fingerprint = fingerprint;
            this.title = title;
            this.count = 1;
            this.firstSeenMs = atMs;
            this.lastSeenMs = atMs;
        }

        void record(long atMs) {
            count++;
            if (atMs > lastSeenMs) {
                lastSeenMs = atMs;
            }
        }

        public String groupKey() {
            return groupKey;
        }

        public String fingerprint() {
            return fingerprint;
        }

        public String title() {
            return title;
        }

        public long count() {
            return count;
        }

        public long firstSeenMs() {
            return firstSeenMs;
        }

        public long lastSeenMs() {
            return lastSeenMs;
        }

        public String status() {
            return status;
        }

        public void resolve() {
            status = RESOLVED;
        }

        public void reopen() {
            status = UNRESOLVED;
        }

        void retitle(String title) {
            this.title = title;
        }
    }

    private final Map<String, Group> groups = new LinkedHashMap<>();

    /** 命中或新建组（组键→组），记录 seen */
    public Group record(String groupKey, String fingerprint, String title, long atMs) {
        Group group = groups.get(groupKey);
        if (group == null) {
            group = new Group(groupKey, fingerprint, title, atMs);
            groups.put(groupKey, group);
        } else {
            group.record(atMs);
            if (title != null && !title.isBlank() && group.title().startsWith("unknown")) {
                group.retitle(title);
            }
        }
        return group;
    }

    public Group get(String groupKey) {
        return groups.get(groupKey);
    }

    public int size() {
        return groups.size();
    }

    /** top 问题：计数降序，平局按 lastSeen 新者优先，再按组键字典序 */
    public List<Group> topProblems(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须为正");
        }
        List<Group> sorted = new ArrayList<>(groups.values());
        sorted.sort((a, b) -> {
            int c = Long.compare(b.count(), a.count());
            if (c != 0) {
                return c;
            }
            c = Long.compare(b.lastSeenMs(), a.lastSeenMs());
            if (c != 0) {
                return c;
            }
            return a.groupKey().compareTo(b.groupKey());
        });
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
}
