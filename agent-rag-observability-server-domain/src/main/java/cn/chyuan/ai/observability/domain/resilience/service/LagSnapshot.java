package cn.chyuan.ai.observability.domain.resilience.service;

import java.util.Map;

/**
 * 消费延迟快照值对象（工单 0220 AD1）
 */
public record LagSnapshot(String topic, long lag, String level, long sampledAt) {

    public LagSnapshot {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        lag = Math.max(0, lag);
    }

    public static LagSnapshot of(String topic, long lag, long warn, long critical, long nowMs) {
        return new LagSnapshot(topic, lag, LagWatermark.grade(lag, warn, critical), nowMs);
    }

    public Map<String, Object> toMap() {
        return Map.of("topic", topic, "lag", lag, "level", level, "sampledAt", sampledAt);
    }
}
