package cn.chyuan.ai.observability.infrastructure.metrics.patrol;

import cn.chyuan.ai.observability.domain.patrol.adapter.port.IPatrolMetricsPort;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 巡检指标适配器（工单 0137 S1）— Micrometer 落 Prometheus：
 * patrol_success_total / patrol_fail_total / patrol_timeout_total /
 * patrol_duration_ms（Timer）/ patrol_score（DistributionSummary）。
 */
@Slf4j
@Component
public class PatrolMetricsAdapter implements IPatrolMetricsPort {

    private final MeterRegistry registry;

    public PatrolMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordProbeFinished(PatrolStatus status, long durationMs, Double score) {
        PatrolStatus safe = status == null ? PatrolStatus.FAIL : status;
        Counter.builder("patrol_" + safe.getCode().toLowerCase() + "_total")
                .description("巡检拨测结果计数（按状态分列）")
                .register(registry)
                .increment();
        registry.timer("patrol_duration_ms").record(Duration.ofMillis(Math.max(0, durationMs)));
        if (score != null) {
            registry.summary("patrol_score").record(score);
        }
    }
}
