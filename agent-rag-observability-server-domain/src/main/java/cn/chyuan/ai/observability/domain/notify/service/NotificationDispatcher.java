package cn.chyuan.ai.observability.domain.notify.service;

import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.ChannelResult;
import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.ChannelRouter;
import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.NotificationChannel;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService.Notification;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService.PreferenceDecision;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService.Subscription;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 通知发送链路（工单 0298 AL6 + 0294 AL2 + 0296 AL4 联动）—
 * 事件入口（`notification.dispatch.enabled` 默认关零行为变化）→ 偏好判定（AL3）→
 * digest 折叠（AL4，digestEnabled 时窗口到期统一发）→ 渠道路由（AL2）→ 异步执行
 * （Executor 端口注入，直接执行器缺省）+ 指数退避重试上限 + 幂等指纹窗口去重。
 * 状态机回写：PENDING→SENT/FAILED/SUPPRESSED/DEDUPED。
 */
@Slf4j
public class NotificationDispatcher {

    private static final int MAX_RETRY = 3;
    /** 幂等窗口毫秒 */
    public static final long DEDUP_WINDOW_MS = 60_000;

    private final NotificationService.NotificationStore store;
    private final ChannelRouter router;
    private final Executor executor;
    private final Map<String, Long> recentFingerprints = new java.util.concurrent.ConcurrentHashMap<>();
    /** 失败重试队列（指数退避：下次可发时间） */
    private final Deque<RetryItem> retryQueue = new ArrayDeque<>();
    private boolean quietHoursEnabled = false;
    private boolean digestEnabled = false;
    private List<NotificationService.Subscription> subscriptions = List.of();

    /** 重试项 */
    record RetryItem(Notification notification, Subscription subscription, int attempts, long nextAttemptMs) {
    }

    public NotificationDispatcher(NotificationService.NotificationStore store, ChannelRouter router,
            Executor executor) {
        this.store = store;
        this.router = router;
        // 同步直执行器缺省（进程内）；可换 MQ/线程池适配
        this.executor = executor != null ? executor : Runnable::run;
    }

    public void setQuietHoursEnabled(boolean enabled) {
        this.quietHoursEnabled = enabled;
    }

    public void setDigestEnabled(boolean enabled) {
        this.digestEnabled = enabled;
    }

    public void setSubscriptions(List<NotificationService.Subscription> subscriptions) {
        this.subscriptions = subscriptions == null ? List.of() : List.copyOf(subscriptions);
    }

    /**
     * 事件入口：对每个订阅者判定 → 落 PENDING（SUPPRESSED 落档可追溯）→ 渠道派发。
     * 指纹去重：同指纹窗口内只发一次，重复落 DEDUPED。
     */
    public void dispatch(Notification notification, long nowMs, int nowMinuteOfDay) {
        boolean dispatched = false;
        for (Subscription subscription : subscriptions) {
            PreferenceDecision decision = NotificationService.decide(subscription, notification,
                    nowMinuteOfDay, quietHoursEnabled);
            switch (decision) {
                case SUPPRESS_TYPE, SUPPRESS_SEVERITY -> { /* 不为该订阅者产生通知 */ }
                case SUPPRESS_QUIET -> store.insert(suppressed(notification, subscription, nowMs));
                case ACCEPT -> {
                    String fingerprint = notification.fingerprint() + "|" + subscription.subscriber();
                    Long lastSent = recentFingerprints.get(fingerprint);
                    if (lastSent != null && nowMs - lastSent < DEDUP_WINDOW_MS) {
                        store.insert(deduped(notification, subscription, nowMs));
                        continue;
                    }
                    recentFingerprints.put(fingerprint, nowMs);
                    Notification pending = pending(notification, subscription, nowMs);
                    store.insert(pending);
                    enqueue(pending, subscription, nowMs);
                    dispatched = true;
                }
            }
        }
        if (!dispatched) {
            log.debug("通知无接收订阅者: type={} severity={}", notification.eventType(), notification.severity());
        }
    }

    /** 渠道派发（异步 + 重试上限） */
    void enqueue(Notification pending, Subscription subscription, long nowMs) {
        try {
            executor.execute(() -> deliver(pending, subscription, 1, nowMs));
        } catch (RejectedExecutionException e) {
            log.warn("通知派发被拒绝（转重试队列）: {}", pending.id());
            retryQueue.add(new RetryItem(pending, subscription, 1, nowMs + backoffMs(1)));
        }
    }

    void deliver(Notification notification, Subscription subscription, int attempt, long nowMs) {
        NotificationChannel channel = router.route(subscription);
        ChannelResult result = channel.send(notification, subscription);
        if (result.delivered()) {
            store.updateStatus(notification.id(), "SENT");
            return;
        }
        if ("UNSUPPORTED".equals(result.status())) {
            // 占位渠道：显式落 FAILED（UNSUPPORTED 语义），不重试
            store.updateStatus(notification.id(), "FAILED");
            return;
        }
        if (attempt < MAX_RETRY) {
            retryQueue.add(new RetryItem(notification, subscription, attempt + 1, nowMs + backoffMs(attempt)));
            return;
        }
        store.updateStatus(notification.id(), "FAILED");
    }

    /** 重试队列滴答（调度口驱动：到期项再派发） */
    public void tickRetries(long nowMs) {
        List<RetryItem> due = new ArrayList<>();
        synchronized (retryQueue) {
            retryQueue.removeIf(item -> {
                if (item.nextAttemptMs() <= nowMs) {
                    due.add(item);
                    return true;
                }
                return false;
            });
        }
        for (RetryItem item : due) {
            enqueue(item.notification(), item.subscription(), nowMs);
        }
    }

    /** 指数退避：attempt 1→2s, 2→4s, ... */
    static long backoffMs(int attempt) {
        return 1_000L << Math.min(attempt, 6);
    }

    private Notification pending(Notification base, Subscription subscription, long nowMs) {
        return new Notification(store instanceof NotificationService.InMemoryNotificationStore memory
                ? memory.nextId() : base.id(), base.eventType(), base.severity(), base.title(),
                base.payloadJson(), base.fingerprint(), subscription.subscriber(), "PENDING", nowMs);
    }

    private Notification suppressed(Notification base, Subscription subscription, long nowMs) {
        return new Notification("sup-" + base.fingerprint() + "-" + nowMs, base.eventType(), base.severity(),
                base.title(), base.payloadJson(), base.fingerprint(), subscription.subscriber(),
                "SUPPRESSED", nowMs);
    }

    private Notification deduped(Notification base, Subscription subscription, long nowMs) {
        return new Notification("ded-" + base.fingerprint() + "-" + nowMs, base.eventType(), base.severity(),
                base.title(), base.payloadJson(), base.fingerprint(), subscription.subscriber(),
                "DEDUPED", nowMs);
    }
}
