package cn.chyuan.ai.observability.domain.aggkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 管道聚合（工单 1216 FG5，elasticsearch 思想）。
 * derivative 相邻桶求导（首桶 null）/cumulative sum 累积/avg_bucket 兄弟桶均值（空集拒绝）。
 */
public final class Pipelines {

    private Pipelines() {
    }

    /** 相邻差分：首桶无前驱为 null */
    public static List<Double> derivative(List<Double> values) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            out.add(i == 0 ? null : values.get(i) - values.get(i - 1));
        }
        return out;
    }

    /** 累积和 */
    public static List<Double> cumulativeSum(List<Double> values) {
        List<Double> out = new ArrayList<>();
        double acc = 0;
        for (Double value : values) {
            acc += value == null ? 0 : value;
            out.add(acc);
        }
        return out;
    }

    /** 兄弟桶均值：空父桶集拒绝 */
    public static double avgBucket(List<Double> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("无父桶可聚合");
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }
}
