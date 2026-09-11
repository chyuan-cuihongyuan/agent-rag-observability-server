package cn.chyuan.ai.observability.domain.health;

/**
 * 组件健康探测端口（工单 0181 Y5）— infrastructure 提供各组件实现（DB/ES/MQ/向量引擎等）。
 */
public interface HealthProbe {

    /** 组件名（db/es/mq/vector…） */
    String name();

    /** 是否关键组件（关键组件 DOWN → 整体 DOWN；非关键 DOWN → DEGRADED） */
    boolean critical();

    /** 执行探测（实现内自计耗时，异常时返回 up=false） */
    ProbeResult probe();

    /** 探测结果 */
    record ProbeResult(String component, boolean up, long latencyMs, String error) {
        public static ProbeResult ok(String component, long latencyMs) {
            return new ProbeResult(component, true, latencyMs, null);
        }

        public static ProbeResult fail(String component, long latencyMs, String error) {
            return new ProbeResult(component, false, latencyMs, error);
        }
    }
}
