package cn.chyuan.ai.observability.domain.evaluate.adapter.port;

/**
 * 评测指标埋点端口 — 由 infrastructure 用 Micrometer 实现，让评测真正产出可监控的 Prometheus 指标。
 */
public interface IEvalMetricsPort {

    /** 任务结束计数，tag: eval_type, status(COMPLETED/FAILED) */
    void recordTaskFinished(String evalType, String status);

    /** 任务耗时分布，tag: eval_type */
    void recordTaskDuration(String evalType, long durationMs);

    /** 单条综合分分布，tag: eval_type */
    void recordScore(String evalType, Double overallScore);

    /** 幻觉命中计数，tag: eval_type */
    void recordHallucination(String evalType);
}
