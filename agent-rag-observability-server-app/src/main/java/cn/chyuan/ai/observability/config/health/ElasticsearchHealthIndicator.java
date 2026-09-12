package cn.chyuan.ai.observability.config.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * ES 依赖健康指示器（SELFLOOP2 loop-217）。
 * 三态：ping 成功 UP / ping false 或异常 DOWN / client 未装配 UNKNOWN（非故障语义）。
 * 默认不进 liveness/readiness 探针组（Boot 4 默认组仅含 state 贡献者；健康 API 位于 org.springframework.boot.health.contributor）。
 * 置于 app 模块（actuator 依赖所在层），client 来自 infrastructure 装配。
 */
@Slf4j
@Component
public class ElasticsearchHealthIndicator implements HealthIndicator {

    private final ElasticsearchClient client;

    @Autowired(required = false)
    public ElasticsearchHealthIndicator(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        if (client == null) {
            return Health.unknown().withDetail("reason", "ElasticsearchClient 未装配").build();
        }
        try {
            boolean ping = client.ping().value();
            if (ping) {
                return Health.up().build();
            }
            return Health.down().withDetail("reason", "ping 返回 false").build();
        } catch (Exception e) {
            log.warn("ES 健康探测失败: {}", e.getMessage());
            return Health.down(e).build();
        }
    }
}
