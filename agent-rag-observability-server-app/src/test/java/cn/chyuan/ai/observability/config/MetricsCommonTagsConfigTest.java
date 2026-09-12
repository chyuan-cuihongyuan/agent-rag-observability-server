package cn.chyuan.ai.observability.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUTOLOOP al-14 / 工单 1014：公共维度注入单测。
 */
class MetricsCommonTagsConfigTest {

    @Test
    void appliesCommonTagsToAllMeters() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsCommonTagsConfig.applyCommonTags(registry, "obs-test", "dev", "host-1");

        registry.counter("observe_request_total", "source_service", "mcp").increment();
        registry.timer("observe_request_duration").record(java.time.Duration.ofMillis(5));

        assertThat(registry.find("observe_request_total").tag("application", "obs-test").counter())
                .isNotNull();
        assertThat(registry.find("observe_request_total").tag("env", "dev").counter())
                .isNotNull();
        assertThat(registry.find("observe_request_total").tag("host", "host-1").counter())
                .isNotNull();
        assertThat(registry.find("observe_request_duration").tag("application", "obs-test").timer())
                .isNotNull();
    }

    @Test
    void businessTagsArePreservedAlongsideCommonTags() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsCommonTagsConfig.applyCommonTags(registry, "obs-test", "prod", "host-2");

        registry.counter("observe_request_total", "source_service", "gateway").increment();

        var counter = registry.find("observe_request_total")
                .tag("source_service", "gateway")
                .tag("env", "prod")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void hostResolutionFailsSafe() {
        // resolveHost 在异常环境回退 unknown，不抛出
        assertThat(MetricsCommonTagsConfig.resolveHost()).isNotBlank();
    }
}
