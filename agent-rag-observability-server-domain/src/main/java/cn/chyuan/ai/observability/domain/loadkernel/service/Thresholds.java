package cn.chyuan.ai.observability.domain.loadkernel.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 阈值判定（工单 1147 EY5，k6 思想）。
 * metric 阈值 rate/p99/avg 越界 breach/多阈值聚合任一 breach 场景失败/无阈值不判。
 */
public final class Thresholds {

    /** 阈值：指标 + 聚合口径 + 算符 + 界值 */
    public record Threshold(String metric, String agg, String op, double bound) {

        public Threshold {
            if (!">".equals(op) && !"<".equals(op)) {
                throw new IllegalArgumentException("阈值算符须 > 或 < : " + op);
            }
        }

        boolean breached(double actual) {
            return ">".equals(op) ? actual > bound : actual < bound;
        }
    }

    private final Map<String, java.util.List<Threshold>> byScenario = new LinkedHashMap<>();

    /** 挂阈值：场景名可先于场景注册（挂靠时校验） */
    public void add(String scenario, Threshold threshold) {
        byScenario.computeIfAbsent(scenario, k -> new java.util.ArrayList<>()).add(threshold);
    }

    /** 判定：actuals 提供 metric→agg→值；任一阈值越界即 breach */
    public boolean breached(String scenario, java.util.function.BiFunction<String, String, Double> actuals) {
        for (Threshold threshold : byScenario.getOrDefault(scenario, java.util.List.of())) {
            Double actual = actuals.apply(threshold.metric(), threshold.agg());
            if (actual != null && threshold.breached(actual)) {
                return true;
            }
        }
        return false;
    }

    public int count(String scenario) {
        return byScenario.getOrDefault(scenario, java.util.List.of()).size();
    }
}
