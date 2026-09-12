package cn.chyuan.ai.observability.domain.resilience.service;

/**
 * SLA 错过记录值对象（工单 0223 AD4，借鉴 Airflow SLA）
 *
 * @param task        调度任务名
 * @param expectedMs  预计完成时长（毫秒）
 * @param actualMs    实际完成时长（毫秒）
 * @param overdueMs   超时毫秒（actual - expected）
 * @param detectedAt  检测时间
 */
public record SlaMiss(String task, long expectedMs, long actualMs, long overdueMs, long detectedAt) {

    public SlaMiss {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("任务名不能为空");
        }
    }

    /** 判定纯函数：actual > expected 返回 miss（含超时毫秒）；未超时返回 null */
    public static SlaMiss judgeIfMissed(String task, long expectedMs, long actualMs, long nowMs) {
        if (expectedMs < 0) {
            expectedMs = 0;
        }
        if (actualMs <= expectedMs) {
            return null;
        }
        return new SlaMiss(task, expectedMs, actualMs, actualMs - expectedMs, nowMs);
    }
}
