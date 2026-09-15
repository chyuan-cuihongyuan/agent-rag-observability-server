package cn.chyuan.ai.observability.infrastructure.metrics.observe;

import cn.chyuan.ai.observability.domain.observe.adapter.metrics.IMetricsPort;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 观测指标 Micrometer 适配器（SELFLOOP6 loop-621，工单 0838/0839，Q1）。
 * <p>
 * token 用量按 OTel GenAI 语义约定命名（gen_ai.usage.input_tokens /
 * output_tokens），DistributionSummary 记录单次调用分布（count=调用数、
 * total=累计 token、max=单次峰值），Grafana 可画成本/容量曲线。
 * 不打 model/provider tag——ChatResultEntity 暂无 model 字段，避免高基数。
 */
@Component
public class MicrometerObserveMetricsAdapter implements IMetricsPort {

    private final MeterRegistry registry;

    public MicrometerObserveMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordTokenUsage(Integer inputTokens, Integer outputTokens) {
        if (inputTokens != null && inputTokens > 0) {
            summary("gen_ai.usage.input_tokens", "LLM 调用 prompt token 用量").record(inputTokens);
        }
        if (outputTokens != null && outputTokens > 0) {
            summary("gen_ai.usage.output_tokens", "LLM 调用 completion token 用量").record(outputTokens);
        }
    }

    private DistributionSummary summary(String name, String description) {
        return DistributionSummary.builder(name)
                .description(description)
                .baseUnit("tokens")
                .publishPercentiles(0.5, 0.95)
                .register(registry);
    }
}
