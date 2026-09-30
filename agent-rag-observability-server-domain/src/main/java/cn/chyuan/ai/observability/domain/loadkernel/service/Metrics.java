package cn.chyuan.ai.observability.domain.loadkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标聚合（工单 1149 EY7，k6 思想）。
 * counter 累加/rate 比率/gauge 取最新/trend 分位聚合/类型错用拒绝。
 */
public final class Metrics {

    /** 指标类型 */
    public enum Kind { COUNTER, RATE, GAUGE, TREND }

    /** 单指标容器 */
    static final class Series {
        final Kind kind;
        final List<Double> samples = new ArrayList<>();
        double counterSum;

        Series(Kind kind) {
            this.kind = kind;
        }

        void add(double value) {
            samples.add(value);
            counterSum += value;
        }

        double aggregate(String agg) {
            if (samples.isEmpty()) {
                throw new IllegalStateException("无样本可聚合");
            }
            switch (agg == null ? kind.name().toLowerCase() : agg) {
                case "sum", "counter" -> {
                    require(Kind.COUNTER, agg);
                    return counterSum;
                }
                case "rate" -> {
                    require(Kind.RATE, agg);
                    long ones = 0;
                    for (double value : samples) {
                        if (value == 1.0) {
                            ones++;
                        }
                    }
                    return (double) ones / samples.size();
                }
                case "latest", "gauge" -> {
                    require(Kind.GAUGE, agg);
                    return samples.get(samples.size() - 1);
                }
                case "p99" -> {
                    require(Kind.TREND, agg);
                    List<Double> sorted = new ArrayList<>(samples);
                    sorted.sort(Double::compare);
                    int index = (int) Math.ceil(0.99 * sorted.size()) - 1;
                    return sorted.get(Math.max(0, index));
                }
                case "avg" -> {
                    require(Kind.TREND, agg);
                    double sum = 0;
                    for (double value : samples) {
                        sum += value;
                    }
                    return sum / samples.size();
                }
                default -> throw new IllegalArgumentException("未知聚合口径: " + agg);
            }
        }

        private void require(Kind expected, String agg) {
            if (kind != expected) {
                throw new IllegalStateException("类型错用: " + kind + " 指标不适用 " + agg + " 聚合");
            }
        }
    }

    private final Map<String, Series> series = new LinkedHashMap<>();

    /** 声明指标：类型唯一、名称唯一 */
    public void declare(String name, String kind) {
        if (series.containsKey(name)) {
            throw new IllegalArgumentException("重复指标: " + name);
        }
        try {
            series.put(name, new Series(Kind.valueOf(kind.toUpperCase())));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知指标类型: " + kind);
        }
    }

    /** 采样 */
    public void sample(String name, double value) {
        require(name).add(value);
    }

    /** 聚合 */
    public double aggregate(String name, String agg) {
        return require(name).aggregate(agg);
    }

    private Series require(String name) {
        Series one = series.get(name);
        if (one == null) {
            throw new IllegalArgumentException("未知指标: " + name);
        }
        return one;
    }
}
