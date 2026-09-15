package cn.chyuan.ai.observability.infrastructure.metrics.observe;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MicrometerObserveMetricsAdapter 单测（SELFLOOP6 loop-621，工单 0838/0839，Q1）。
 * gen_ai.usage.* 命名对齐 + null/零值过滤。
 */
class MicrometerObserveMetricsAdapterTest {

    private SimpleMeterRegistry registry;
    private MicrometerObserveMetricsAdapter adapter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        adapter = new MicrometerObserveMetricsAdapter(registry);
    }

    @Test
    @DisplayName("正常记录：input/output 双 summary 计数与总量")
    void recordsBothDirections() {
        adapter.recordTokenUsage(500, 120);
        adapter.recordTokenUsage(300, 80);

        var input = registry.get("gen_ai.usage.input_tokens").summary();
        var output = registry.get("gen_ai.usage.output_tokens").summary();
        assertThat(input.count()).isEqualTo(2);
        assertThat(input.totalAmount()).isEqualTo(800);
        assertThat(output.count()).isEqualTo(2);
        assertThat(output.totalAmount()).isEqualTo(200);
        assertThat(input.getId().getBaseUnit()).isEqualTo("tokens");
        assertThat(input.getId().getDescription()).isNotNull();
    }

    @Test
    @DisplayName("null/零值不记录（避免空数据污染分布）")
    void skipsNullAndZero() {
        adapter.recordTokenUsage(null, null);
        adapter.recordTokenUsage(0, 0);

        assertThat(registry.find("gen_ai.usage.input_tokens").summary()).isNull();
        assertThat(registry.find("gen_ai.usage.output_tokens").summary()).isNull();
    }

    @Test
    @DisplayName("单侧缺失：只记录存在的一侧")
    void recordsPresentSideOnly() {
        adapter.recordTokenUsage(400, null);

        assertThat(registry.get("gen_ai.usage.input_tokens").summary().count()).isEqualTo(1);
        assertThat(registry.find("gen_ai.usage.output_tokens").summary()).isNull();
    }
}
