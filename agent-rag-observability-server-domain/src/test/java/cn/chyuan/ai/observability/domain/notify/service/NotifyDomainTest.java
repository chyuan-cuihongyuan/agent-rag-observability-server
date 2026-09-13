package cn.chyuan.ai.observability.domain.notify.service;

import cn.chyuan.ai.observability.domain.notify.service.NotificationService.InMemoryNotificationStore;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService.Notification;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService.PreferenceDecision;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService.Subscription;
import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.ChannelResult;
import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.ChannelRouter;
import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.EmailPlaceholderChannel;
import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.InboxChannel;
import cn.chyuan.ai.observability.domain.notify.service.NotificationChannels.WebhookChannel;
import cn.chyuan.ai.observability.domain.notify.service.DigestWindowCollapser.CollapsibleItem;
import cn.chyuan.ai.observability.domain.notify.service.DigestWindowCollapser.DigestLine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知域单测（工单 0293-0298 AL1-AL6）：模型状态机/偏好判定（静默+CRITICAL 穿透+跨零点）/
 * 渠道三适配器/digest 折叠/模板渲染/派发重试与幂等去重。
 */
class NotifyDomainTest {

    private Notification notification(String id, String severity, long atMs) {
        return new Notification(id, "QUALITY_FAIL", severity, "质量告警", "{}",
                DigestWindowCollapser.fingerprint("QUALITY_FAIL", "urn:agent:dataset:a"), "ops", "PENDING", atMs);
    }

    @Test
    void 偏好判定_类型严重级静默与穿透() {
        Subscription sub = new Subscription("ops", Set.of("QUALITY_FAIL"), "WARNING",
                22 * 60, 7 * 60, "INBOX");
        // 静默时段（23:00）WARNING 抑制
        assertEquals(PreferenceDecision.SUPPRESS_QUIET, NotificationService.decide(sub,
                notification("n1", "WARNING", 0), 23 * 60, true));
        // CRITICAL 穿透静默
        assertEquals(PreferenceDecision.ACCEPT, NotificationService.decide(sub,
                notification("n2", "CRITICAL", 0), 23 * 60, true));
        // 白天 INFO 严重级不足
        assertEquals(PreferenceDecision.SUPPRESS_SEVERITY, NotificationService.decide(sub,
                notification("n3", "INFO", 0), 10 * 60, true));
        // 类型不匹配
        assertEquals(PreferenceDecision.SUPPRESS_TYPE, NotificationService.decide(sub,
                new Notification("n4", "SLA_MISS", "WARNING", "t", "{}", "fp", "ops", "PENDING", 0),
                10 * 60, true));
        // 跨零点静默窗（22:00-07:00）：03:00 抑制、08:00 放行
        assertEquals(PreferenceDecision.SUPPRESS_QUIET, NotificationService.decide(sub,
                notification("n5", "WARNING", 0), 3 * 60, true));
        assertEquals(PreferenceDecision.ACCEPT, NotificationService.decide(sub,
                notification("n6", "WARNING", 0), 8 * 60, true));
    }

    @Test
    void 渠道三适配器与路由() {
        ChannelRouter router = new ChannelRouter()
                .register(new InboxChannel())
                .register(new EmailPlaceholderChannel())
                .register(new WebhookChannel((url, signature, body) -> "200"));
        Notification notification = notification("n1", "WARNING", 0);
        assertTrue(router.route(new Subscription("ops", Set.of(), "INFO", 0, 0, "INBOX"))
                .send(notification, null).delivered());
        // webhook 成功（2xx）
        Subscription webhookSub = new Subscription("ops", Set.of(), "INFO", 0, 0,
                "WEBHOOK|https://hook.example/x");
        ChannelResult hook = router.route(webhookSub).send(notification, webhookSub);
        assertTrue(hook.delivered());
        // webhook 缺 url
        assertFalse(router.route(new Subscription("ops", Set.of(), "INFO", 0, 0, "WEBHOOK")).send(notification, null).delivered());
        // 邮件占位 UNSUPPORTED
        ChannelResult email = router.route(new Subscription("ops", Set.of(), "INFO", 0, 0, "EMAIL"))
                .send(notification, null);
        assertEquals("UNSUPPORTED", email.status());
        // 未知渠道回退 INBOX
        assertEquals("INBOX", router.route(new Subscription("ops", Set.of(), "INFO", 0, 0, "BOGUS")).type());
    }

    @Test
    void digest折叠与最高严重级归并() {
        List<DigestLine> lines = DigestWindowCollapser.collapse(List.of(
                new CollapsibleItem("1", "QUALITY_FAIL", "WARNING", "urn:a", 1_000),
                new CollapsibleItem("2", "QUALITY_FAIL", "CRITICAL", "urn:a", 2_000),
                new CollapsibleItem("3", "SLA_MISS", "WARNING", "urn:b", 3_000)));
        assertEquals(2, lines.size());
        DigestLine quality = lines.get(0);
        assertEquals(2, quality.count());
        assertEquals("CRITICAL", quality.highestSeverity());
        assertEquals(1_000, quality.firstAtMs());
        assertEquals(2_000, quality.lastAtMs());
        // 指纹归一化稳定
        assertEquals(DigestWindowCollapser.fingerprint("T", " Urn:X "),
                DigestWindowCollapser.fingerprint("t", "urn:x"));
    }

    @Test
    void 模板渲染_缺失变量与转义() {
        TemplateRenderer renderer = new TemplateRenderer();
        assertEquals("&lt;b&gt;ok&lt;/b&gt; 3", renderer.render("{{tag}} {{count}}",
                Map.of("tag", "<b>ok</b>", "count", "3")));
        TemplateRenderer.RenderException e = assertThrows(TemplateRenderer.RenderException.class,
                () -> renderer.render("{{missing}} {{missing}} {{x}}", Map.of("x", "1")));
        assertTrue(e.getMessage().contains("missing"));
        // 模板存档版本递增
        renderer.save(new TemplateRenderer.Template("t1", 0, "QUALITY_FAIL", "body1", "op"));
        assertEquals("body1", renderer.defaultFor("QUALITY_FAIL").body());
    }

    @Test
    void 派发链路_状态机_重试与去重() {
        InMemoryNotificationStore store = new InMemoryNotificationStore();
        AtomicInteger attempts = new AtomicInteger();
        ChannelRouter router = new ChannelRouter()
                .register(new NotificationChannels.NotificationChannel() {
                    @Override
                    public String type() {
                        return "INBOX";
                    }

                    @Override
                    public ChannelResult send(Notification n, Subscription s) {
                        // 前两次失败，第三次成功
                        return attempts.incrementAndGet() <= 2
                                ? ChannelResult.fail("boom") : ChannelResult.ok("ok");
                    }
                });
        NotificationDispatcher dispatcher = new NotificationDispatcher(store, router, null);
        dispatcher.setSubscriptions(List.of(new Subscription("ops", Set.of("QUALITY_FAIL"), "INFO", 0, 0, "INBOX")));
        long now = 10_000;
        dispatcher.dispatch(notification("n1", "INFO", now), now, 12 * 60);
        // 同步直执行器：第一次失败落重试队列
        assertTrue(store.query("ops", "PENDING", 0, 10).size() >= 1);
        // 重试滴答（退避到期）直至成功
        dispatcher.tickRetries(now + 2_000);
        dispatcher.tickRetries(now + 6_000);
        List<Notification> sent = store.query("ops", "SENT", 0, 10);
        assertEquals(1, sent.size());
        // 幂等去重：同指纹 60s 窗口内重复派发落 DEDUPED
        dispatcher.dispatch(notification("n2", "INFO", now + 1_000), now + 1_000, 12 * 60);
        assertEquals(1, store.query("ops", "DEDUPED", 0, 10).size());
        // 已读闭环（AL7 语义由 store 承担）：按实际派发 id 标记
        String sentId = sent.get(0).id();
        store.updateStatus(sentId, "READ");
        assertEquals(0, store.unreadCount("ops"));
    }
}
