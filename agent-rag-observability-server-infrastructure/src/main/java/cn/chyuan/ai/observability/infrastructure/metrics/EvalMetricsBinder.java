package cn.chyuan.ai.observability.infrastructure.metrics;

import cn.chyuan.ai.observability.domain.evaluate.service.EvalConcurrencyGuard;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 评测并发水位指标绑定（SELFLOOP4 loop-407，工单 0612/0613）。
 * <p>
 * 把 domain 侧 {@link EvalConcurrencyGuard} 闸门状态暴露为 observe_ 指标家族：
 * 活跃任务数 / 剩余许可 / 上限（Gauge）+ 累计拒绝（FunctionCounter，单调语义）。
 * domain 保持零框架依赖，绑定收口在 infrastructure。
 * <p>
 * 借鉴来源：Micrometer 官方「仪表绑定层」惯例（gauge 绑定活性状态、
 * FunctionCounter 绑定单调量）。
 */
@Component
public class EvalMetricsBinder {

    @Resource
    private MeterRegistry meterRegistry;

    @Resource
    private EvalConcurrencyGuard evalConcurrencyGuard;

    @PostConstruct
    public void bind() {
        Gauge.builder("observe_eval_active_tasks", evalConcurrencyGuard,
                        g -> g.maxPermits() - g.availablePermits())
                .description("当前正在执行的评测任务数")
                .register(meterRegistry);
        Gauge.builder("observe_eval_permits_available", evalConcurrencyGuard,
                        EvalConcurrencyGuard::availablePermits)
                .description("评测并发闸门剩余许可")
                .register(meterRegistry);
        Gauge.builder("observe_eval_permits_max", evalConcurrencyGuard,
                        EvalConcurrencyGuard::maxPermits)
                .description("评测并发闸门上限")
                .register(meterRegistry);
        FunctionCounter.builder("observe_eval_rejected_total", evalConcurrencyGuard,
                        EvalConcurrencyGuard::rejectionCount)
                .description("评测任务因并发饱和被累计拒绝次数")
                .register(meterRegistry);
    }
}
