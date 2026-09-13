package cn.chyuan.ai.observability.domain.notify.service;

import cn.chyuan.ai.observability.domain.notify.service.NotificationService.Notification;
import cn.chyuan.ai.observability.domain.notify.service.NotificationService.Subscription;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道端口与适配器（工单 0294 AL2，借鉴 Novu channels）—
 * WEBHOOK（HTTP 客户端端口注入+签名头）/ INBOX（站内信落库）/ EMAIL（显式占位返回 UNSUPPORTED）；
 * ChannelRouter 按订阅者偏好选渠道（无偏好默认 INBOX）。
 */
public class NotificationChannels {

    /** 渠道发送结果 */
    public record ChannelResult(boolean delivered, String status, String detail) {

        public static ChannelResult ok(String detail) {
            return new ChannelResult(true, "SENT", detail);
        }

        public static ChannelResult fail(String detail) {
            return new ChannelResult(false, "FAILED", detail);
        }

        public static ChannelResult unsupported(String detail) {
            return new ChannelResult(false, "UNSUPPORTED", detail);
        }
    }

    /** 渠道端口 */
    public interface NotificationChannel {

        String type();

        ChannelResult send(Notification notification, Subscription subscription);
    }

    /** HTTP 客户端端口（webhook 适配器注入；测试 mock） */
    public interface HttpPort {

        /** 返回 2xx 视为成功；超时/非 2xx 返回 detail */
        String postJson(String url, String signatureHeader, String body);
    }

    /** WEBHOOK 适配器 */
    public static class WebhookChannel implements NotificationChannel {

        private final HttpPort http;

        public WebhookChannel(HttpPort http) {
            this.http = http;
        }

        @Override
        public String type() {
            return "WEBHOOK";
        }

        @Override
        public ChannelResult send(Notification notification, Subscription subscription) {
            String url = webhookUrlOf(subscription);
            if (url == null || url.isBlank()) {
                return ChannelResult.fail("缺少 webhook url");
            }
            String body = "{\"id\":\"" + notification.id() + "\",\"type\":\"" + notification.eventType()
                    + "\",\"severity\":\"" + notification.severity() + "\",\"title\":\""
                    + notification.title() + "\"}";
            String detail = http.postJson(url, "X-Signature: sha256=" + notification.fingerprint(), body);
            return detail != null && detail.startsWith("2") ? ChannelResult.ok(detail) : ChannelResult.fail(detail);
        }

        private String webhookUrlOf(Subscription subscription) {
            if (subscription == null || subscription.channel() == null) {
                return null;
            }
            // 订阅者字段语义复用：channel 配置形如 "WEBHOOK|https://hook.example/xxx"
            String channel = subscription.channel();
            return channel.startsWith("WEBHOOK|") ? channel.substring("WEBHOOK|".length()) : null;
        }
    }

    /** INBOX 适配器（站内信落库：经 NotificationStore.updateStatus 由派发器回写，适配器仅确认） */
    public static class InboxChannel implements NotificationChannel {

        @Override
        public String type() {
            return "INBOX";
        }

        @Override
        public ChannelResult send(Notification notification, Subscription subscription) {
            return ChannelResult.ok("inbox:" + notification.subscriber());
        }
    }

    /** EMAIL 占位适配器（0249-D8：真实通道对接出界） */
    public static class EmailPlaceholderChannel implements NotificationChannel {

        @Override
        public String type() {
            return "EMAIL";
        }

        @Override
        public ChannelResult send(Notification notification, Subscription subscription) {
            return ChannelResult.unsupported("邮件通道为占位适配（未对接 SMTP）");
        }
    }

    /** 渠道路由：按订阅偏好选择渠道（无偏好默认 INBOX） */
    public static class ChannelRouter {

        private final Map<String, NotificationChannel> channels = new LinkedHashMap<>();

        public ChannelRouter register(NotificationChannel channel) {
            channels.put(channel.type(), channel);
            return this;
        }

        public NotificationChannel route(Subscription subscription) {
            String type = subscription == null || subscription.channel() == null ? "INBOX" : subscription.channel();
            String base = type.contains("|") ? type.substring(0, type.indexOf('|')) : type;
            NotificationChannel channel = channels.getOrDefault(base, channels.get("INBOX"));
            return channel != null ? channel : new InboxChannel();
        }

        public Map<String, NotificationChannel> channels() {
            return channels;
        }
    }
}
