package cn.chyuan.ai.observability.domain.patrol.adapter.port;

import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;

/**
 * 巡检指标端口（工单 0137 S1）— infrastructure 用 Micrometer 实现 Prometheus 打点：
 * patrol_success_total / patrol_fail_total / patrol_timeout_total /
 * patrol_duration_ms / patrol_score。
 */
public interface IPatrolMetricsPort {

    /** 单次拨测结果打点（状态计数 + 耗时 + 轻量分，score 可空） */
    void recordProbeFinished(PatrolStatus status, long durationMs, Double score);
}
