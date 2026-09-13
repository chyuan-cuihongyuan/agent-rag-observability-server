package cn.chyuan.ai.observability.domain.notify.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知域模型与订阅偏好（工单 0293 AL1 + 0295 AL3，借鉴 Novu 通知模型）—
 * Notification（事件类型/严重级/状态机 PENDING|SENT|FAILED|SUPPRESSED|DIGESTED|DEDUPED）+
 * Subscription（订阅者/事件类型集/最低严重级/静默时段）；偏好判定纯函数
 * （类型命中 + 严重级≥阈值 + 静默时段抑制，CRITICAL 穿透静默）。
 * 存储经 {@link NotificationStore} 端口（notification 观测库第 31 表双方言）。
 */
public class NotificationService {

    public static final String SEVERITY_INFO = "INFO";
    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_CRITICAL = "CRITICAL";
    private static final Map<String, Integer> SEVERITY_RANK =
            Map.of(SEVERITY_INFO, 0, SEVERITY_WARNING, 1, SEVERITY_CRITICAL, 2);

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SUPPRESSED = "SUPPRESSED";
    public static final String STATUS_DIGESTED = "DIGESTED";
    public static final String STATUS_DEDUPED = "DEDUPED";

    /** 通知实体 */
    public record Notification(String id, String eventType, String severity, String title,
            String payloadJson, String fingerprint, String subscriber, String status, long createdAtMs) {

        public Notification {
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType 不能为空");
            }
            if (!SEVERITY_RANK.containsKey(severity)) {
                throw new IllegalArgumentException("非法严重级: " + severity);
            }
        }

        public int severityRank() {
            return SEVERITY_RANK.get(severity);
        }
    }

    /** 订阅偏好值对象（quietMinutes 跨零点：start >= end 视为跨天窗） */
    public record Subscription(String subscriber, Set<String> eventTypes, String minSeverity,
            int quietStartMinute, int quietEndMinute, String channel) {

        public Subscription {
            if (subscriber == null || subscriber.isBlank()) {
                throw new IllegalArgumentException("subscriber 不能为空");
            }
            if (!SEVERITY_RANK.containsKey(minSeverity)) {
                throw new IllegalArgumentException("非法最低严重级: " + minSeverity);
            }
            eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
            if (channel == null || channel.isBlank()) {
                channel = "INBOX";
            }
        }
    }

    /** 偏好判定结果 */
    public enum PreferenceDecision {
        ACCEPT, SUPPRESS_QUIET, SUPPRESS_SEVERITY, SUPPRESS_TYPE
    }

    /** 通知存储端口（notification 第 31 表；内存缺省） */
    public interface NotificationStore {

        void insert(Notification notification);

        void updateStatus(String id, String status);

        List<Notification> query(String subscriber, String status, int offset, int limit);

        int unreadCount(String subscriber);

        List<Subscription> listSubscriptions();

        void upsertSubscription(Subscription subscription);

        Subscription findSubscription(String subscriber);
    }

    /** 内存缺省存储 */
    public static class InMemoryNotificationStore implements NotificationStore {
        private final Map<String, Notification> rows = new ConcurrentHashMap<>();
        private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
        private long sequence = 0;

        @Override
        public void insert(Notification notification) {
            rows.put(notification.id(), notification);
        }

        @Override
        public void updateStatus(String id, String status) {
            Notification current = rows.get(id);
            if (current != null) {
                rows.put(id, new Notification(current.id(), current.eventType(), current.severity(),
                        current.title(), current.payloadJson(), current.fingerprint(), current.subscriber(),
                        status, current.createdAtMs()));
            }
        }

        @Override
        public List<Notification> query(String subscriber, String status, int offset, int limit) {
            return rows.values().stream()
                    .filter(n -> subscriber == null || n.subscriber().equals(subscriber))
                    .filter(n -> status == null || status.isBlank() || n.status().equals(status))
                    .sorted(Comparator.comparingLong(Notification::createdAtMs).reversed())
                    .skip(Math.max(0, offset))
                    .limit(Math.max(1, limit))
                    .toList();
        }

        @Override
        public int unreadCount(String subscriber) {
            return (int) rows.values().stream()
                    .filter(n -> n.subscriber().equals(subscriber))
                    .filter(n -> STATUS_PENDING.equals(n.status()) || STATUS_SENT.equals(n.status()))
                    .count();
        }

        @Override
        public List<Subscription> listSubscriptions() {
            return List.copyOf(subscriptions.values());
        }

        @Override
        public void upsertSubscription(Subscription subscription) {
            subscriptions.put(subscription.subscriber(), subscription);
        }

        @Override
        public Subscription findSubscription(String subscriber) {
            return subscriptions.get(subscriber);
        }

        /** 供派发器登记新通知（id 生成） */
        public synchronized String nextId() {
            return "ntf-" + (++sequence);
        }
    }

    /**
     * 偏好判定（纯函数，Clock 分钟注入）：CRITICAL 穿透静默；类型不匹配/严重级不足/静默命中 → 抑制。
     */
    public static PreferenceDecision decide(Subscription subscription, Notification notification,
            int nowMinuteOfDay, boolean quietHoursEnabled) {
        if (!subscription.eventTypes().isEmpty() && !subscription.eventTypes().contains(notification.eventType())) {
            return PreferenceDecision.SUPPRESS_TYPE;
        }
        if (notification.severityRank() < SEVERITY_RANK.get(subscription.minSeverity())) {
            return PreferenceDecision.SUPPRESS_SEVERITY;
        }
        if (quietHoursEnabled && !SEVERITY_CRITICAL.equals(notification.severity())
                && inQuietHours(nowMinuteOfDay, subscription.quietStartMinute(), subscription.quietEndMinute())) {
            return PreferenceDecision.SUPPRESS_QUIET;
        }
        return PreferenceDecision.ACCEPT;
    }

    /** 静默时段判定（支持跨零点：start > end） */
    public static boolean inQuietHours(int nowMinute, int start, int end) {
        if (start == end) {
            return false;
        }
        return start < end ? nowMinute >= start && nowMinute < end
                : nowMinute >= start || nowMinute < end;
    }
}
