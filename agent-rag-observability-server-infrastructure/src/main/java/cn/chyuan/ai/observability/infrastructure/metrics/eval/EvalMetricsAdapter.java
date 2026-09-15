package cn.chyuan.ai.observability.infrastructure.metrics.eval;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IEvalMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 评测指标适配器 — 用 Micrometer 记录评测相关 Prometheus 指标。
 * 让评测任务真正产出可监控的 metrics。
 */
@Slf4j
@Component
public class EvalMetricsAdapter implements IEvalMetricsPort {

    private final MeterRegistry registry;

    public EvalMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordTaskFinished(String evalType, String status) {
        Counter.builder("eval_task_finished")
                .description("评测任务完成计数")
                .tags(Tags.of("eval_type", nullToDefault(evalType), "status", nullToDefault(status)))
                .register(registry)
                .increment();
    }

    @Override
    public void recordTaskDuration(String evalType, long durationMs) {
        // builder 式 + 客户端分位（O46）：直接渲染 _p50/_p95，Simple/Prometheus 双端可读。
        // 评测任务每任务一记录，量级低，客户端聚合开销可忽略。
        Timer.builder("eval_task_duration_ms")
                .description("评测任务端到端耗时（含 COMPLETED/FAILED/空数据集全路径）")
                .tags(Tags.of("eval_type", nullToDefault(evalType)))
                .publishPercentiles(0.5, 0.95)
                .register(registry)
                .record(java.time.Duration.ofMillis(durationMs));
    }

    @Override
    public void recordScore(String evalType, Double overallScore) {
        registry.summary("eval_score", "eval_type", nullToDefault(evalType))
                .record(overallScore != null ? overallScore : 0.0);
    }

    @Override
    public void recordHallucination(String evalType) {
        Counter.builder("eval_hallucination_hit")
                .description("评测幻觉命中计数")
                .tags(Tags.of("eval_type", nullToDefault(evalType)))
                .register(registry)
                .increment();
    }

    private String nullToDefault(String s) {
        return s == null || s.isEmpty() ? "unknown" : s;
    }
}
