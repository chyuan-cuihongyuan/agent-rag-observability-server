package cn.chyuan.ai.observability.domain.health;

import java.util.List;
import java.util.Map;

/**
 * 深度健康聚合（工单 0181 Y5）— 纯函数：
 * 整体判定 UP（全部组件 up）/ DEGRADED（存在非关键组件 down）/ DOWN（任一关键组件 down）。
 */
public class HealthAggregator {

    public static final String UP = "UP";
    public static final String DEGRADED = "DEGRADED";
    public static final String DOWN = "DOWN";

    /**
     * @param results 各组件探测结果
     * @param criticalFlags 与 results 一一对应的关键性标记
     */
    public static String overall(List<HealthProbe.ProbeResult> results, List<Boolean> criticalFlags) {
        boolean anyDown = false;
        boolean criticalDown = false;
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i).up()) {
                anyDown = true;
                if (i < criticalFlags.size() && Boolean.TRUE.equals(criticalFlags.get(i))) {
                    criticalDown = true;
                }
            }
        }
        if (criticalDown) {
            return DOWN;
        }
        return anyDown ? DEGRADED : UP;
    }

    /** 单组件结果转 map（端点输出形态） */
    public static Map<String, Object> toMap(HealthProbe.ProbeResult r) {
        return Map.of(
                "component", r.component(),
                "status", r.up() ? "UP" : "DOWN",
                "latencyMs", r.latencyMs(),
                "error", r.error() == null ? "" : r.error());
    }
}
