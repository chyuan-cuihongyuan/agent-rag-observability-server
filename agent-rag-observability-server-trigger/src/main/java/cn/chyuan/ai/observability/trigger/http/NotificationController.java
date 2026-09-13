package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.ChannelRouter;
import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.EmailPlaceholderChannel;
import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.InboxChannel;
import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.WebhookChannel;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService.Notification;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService.NotificationStore;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService.Subscription;
import cn.chyuan.ai.observability.domain.notify.service.NotificationDispatcher;
import cn.chyuan.ai.observability.domain.notify.service.DigestWindowCollapser;
import cn.chyuan.ai.observability.domain.notify.service.DigestWindowCollapser.DigestLine;
import cn.chyuan.ai.observability.domain.notify.service.TemplateRenderer;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通知中心端点（六期 AL 簇 0293-0300）—
 * 事件触发（默认关）、订阅偏好管理、digest 预览、查询/未读/已读（订阅者隔离）。
 * 渠道：WEBHOOK/INBOX 真实可用 + EMAIL 占位（0249-D8）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationStore store;
    private final NotificationDispatcher dispatcher;
    private final TemplateRenderer renderer;

    @Value("${notification.dispatch.enabled:false}")
    private boolean dispatchEnabled;

    public NotificationController(@Autowired(required = false) NotificationStore store,
            @Autowired(required = false) NotificationDispatcher dispatcher,
            @Autowired(required = false) TemplateRenderer renderer) {
        this.store = store != null ? store : new NotificationService.InMemoryNotificationStore();
        ChannelRouter router = new ChannelRouter()
                .register(new InboxChannel())
                .register(new WebhookChannel((url, signature, body) -> "0"))
                .register(new EmailPlaceholderChannel());
        this.dispatcher = dispatcher != null ? dispatcher : new NotificationDispatcher(this.store, router, null);
        this.renderer = renderer != null ? renderer : new TemplateRenderer();
    }

    /** AL3/AL1：登记/更新订阅偏好 */
    @PostMapping("/subscriptions")
    public Response<Subscription> upsertSubscription(@RequestBody Subscription subscription) {
        try {
            dispatcher.setSubscriptions(merge(store, subscription));
            store.upsertSubscription(subscription);
            return Response.success(subscription);
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    private static List<Subscription> merge(NotificationStore store, Subscription subscription) {
        return store.listSubscriptions().stream()
                .map(existing -> existing.subscriber().equals(subscription.subscriber())
                        ? subscription : existing)
                .collect(java.util.stream.Collectors.toList());
    }

    /** AL6/AL3：事件触发入口（默认关：dispatchEnabled=false 时返回 skipped） */
    @PostMapping("/events")
    public Response<Map<String, Object>> trigger(@RequestBody Map<String, String> body) {
        if (!dispatchEnabled) {
            return Response.success(Map.of("skipped", true, "reason", "notification.dispatch.enabled=false"));
        }
        try {
            long nowMs = System.currentTimeMillis();
            Notification notification = new Notification(
                    ((NotificationService.InMemoryNotificationStore) store).nextId(),
                    body.getOrDefault("eventType", "GENERIC"),
                    body.getOrDefault("severity", NotificationService.SEVERITY_INFO),
                    body.getOrDefault("title", ""), body.get("payloadJson"),
                    body.getOrDefault("fingerprint", "-"), "-", "PENDING", nowMs);
            dispatcher.dispatch(notification, nowMs, nowMinute(nowMs));
            dispatcher.tickRetries(nowMs);
            return Response.success(Map.of("accepted", true));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    /** AL4：digest 预览（窗口折叠纯函数透出） */
    @PostMapping("/digest-preview")
    public Response<List<DigestWindowCollapser.DigestLine>> digestPreview(
            @RequestBody List<DigestWindowCollapser.CollapsibleItem> items) {
        return Response.success(DigestWindowCollapser.collapse(items));
    }

    /** AL7：通知列表（订阅者隔离 + 分页） */
    @GetMapping
    public Response<List<Notification>> list(@RequestParam String subscriber,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
        return Response.success(store.query(subscriber, status, offset, Math.min(limit, 200)));
    }

    /** AL7：未读计数 */
    @GetMapping("/unread-count")
    public Response<Map<String, Integer>> unreadCount(@RequestParam String subscriber) {
        return Response.success(Map.of("unread", store.unreadCount(subscriber)));
    }

    /** AL7：单条已读（幂等） */
    @PostMapping("/{id}/read")
    public Response<Map<String, Object>> markRead(@PathVariable String id,
            @RequestParam String subscriber) {
        Notification notification = store.query(subscriber, null, 0, 10_000).stream()
                .filter(n -> n.id().equals(id)).findFirst().orElse(null);
        if (notification == null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "通知不存在或无权访问");
        }
        store.updateStatus(id, "READ");
        return Response.success(Map.of("id", id, "status", "READ"));
    }

    /** AL7：全部已读 */
    @PostMapping("/read-all")
    public Response<Map<String, Integer>> readAll(@RequestParam String subscriber) {
        List<Notification> unread = store.query(subscriber, null, 0, 10_000).stream()
                .filter(n -> !n.status().equals("READ") && !n.status().equals(NotificationService.STATUS_SUPPRESSED)
                        && !n.status().equals(NotificationService.STATUS_DEDUPED))
                .toList();
        unread.forEach(n -> store.updateStatus(n.id(), "READ"));
        return Response.success(Map.of("read", unread.size()));
    }

    /** AL5：模板试渲染预览（不发送） */
    @PostMapping("/render-preview")
    public Response<Map<String, Object>> renderPreview(@RequestBody Map<String, String> body) {
        try {
            String rendered = renderer.render(body.get("template"), body);
            Map<String, Object> out = new java.util.HashMap<>();
            out.put("rendered", rendered);
            out.put("missing", java.util.List.of());
            return Response.success(out);
        } catch (TemplateRenderer.RenderException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    static int nowMinute(long nowMs) {
        return (int) ((nowMs / 60_000) % (24 * 60));
    }
}
