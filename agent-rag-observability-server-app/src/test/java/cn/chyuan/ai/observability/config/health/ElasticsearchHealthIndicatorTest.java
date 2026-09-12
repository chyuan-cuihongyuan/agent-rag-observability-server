package cn.chyuan.ai.observability.config.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * ES 健康指示器三态契约（SELFLOOP2 loop-217）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ElasticsearchHealthIndicator 三态契约")
class ElasticsearchHealthIndicatorTest {

    @Mock
    private ElasticsearchClient client;

    @Test
    @DisplayName("ping true → UP")
    void pingTrueIsUp() throws Exception {
        when(client.ping()).thenReturn(new BooleanResponse(true));
        Health health = new ElasticsearchHealthIndicator(client).health();
        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    @DisplayName("ping false → DOWN（带 reason）")
    void pingFalseIsDown() throws Exception {
        when(client.ping()).thenReturn(new BooleanResponse(false));
        Health health = new ElasticsearchHealthIndicator(client).health();
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("ping 返回 false", health.getDetails().get("reason"));
    }

    @Test
    @DisplayName("ping 抛异常 → DOWN（带异常详情）")
    void pingExceptionIsDown() throws Exception {
        when(client.ping()).thenThrow(new RuntimeException("connection refused"));
        Health health = new ElasticsearchHealthIndicator(client).health();
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().toString().contains("connection refused"));
    }

    @Test
    @DisplayName("client 未装配 → UNKNOWN（非故障语义）")
    void missingClientIsUnknown() {
        Health health = new ElasticsearchHealthIndicator(null).health();
        assertEquals(Status.UNKNOWN, health.getStatus());
        assertEquals("ElasticsearchClient 未装配", health.getDetails().get("reason"));
    }
}
