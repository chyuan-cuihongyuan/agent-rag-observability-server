package cn.chyuan.ai.observability.domain.tskernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 降采样（工单 0483 BF4，influxdb retention/prometheus staleness 思想）。
 * 窗口聚合（avg/sum/max/min/count）/边界对齐（floorDiv 窗口起点）/
 * 多精度层（原始→5m→1h）链式降采样。
 */
public class Downsampler {

    /** 聚合算子 */
    public enum Aggregator {
        AVG, SUM, MAX, MIN, COUNT
    }

    /** 降采样点：窗口起点 + 聚合值 */
    public record Point(long windowStart, double value) {
    }

    private final long windowMillis;
    private final Aggregator aggregator;

    public Downsampler(long windowMillis, Aggregator aggregator) {
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("窗口须 > 0: " + windowMillis);
        }
        this.windowMillis = windowMillis;
        this.aggregator = aggregator;
    }

    /** 降采样：窗口边界对齐（[start, start+window)），窗口内聚合 */
    public List<Point> downsample(List<TimeBlocker.Sample> samples) {
        List<TimeBlocker.Sample> sorted = new ArrayList<>(samples);
        sorted.sort(java.util.Comparator.comparingLong(TimeBlocker.Sample::timestamp));
        List<Point> points = new ArrayList<>();
        int index = 0;
        while (index < sorted.size()) {
            long start = Math.floorDiv(sorted.get(index).timestamp(), windowMillis) * windowMillis;
            long end = start + windowMillis;
            List<Double> window = new ArrayList<>();
            while (index < sorted.size() && sorted.get(index).timestamp() < end) {
                window.add(sorted.get(index).value());
                index++;
            }
            points.add(new Point(start, aggregate(window)));
        }
        return points;
    }

    /** 链式降采样：原始 → 5m → 1h（逐层窗口取大，算子可不同） */
    public static List<Point> chain(List<TimeBlocker.Sample> samples, List<Downsampler> layers) {
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("至少一层降采样");
        }
        List<TimeBlocker.Sample> current = samples;
        List<Point> result = List.of();
        for (Downsampler layer : layers) {
            result = layer.downsample(current);
            current = result.stream()
                    .map(point -> new TimeBlocker.Sample(point.windowStart(), point.value()))
                    .toList();
        }
        return result;
    }

    private double aggregate(List<Double> values) {
        if (values.isEmpty()) {
            throw new IllegalStateException("空窗口不聚合");
        }
        return switch (aggregator) {
            case AVG -> values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            case SUM -> values.stream().mapToDouble(Double::doubleValue).sum();
            case MAX -> values.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            case MIN -> values.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            case COUNT -> values.size();
        };
    }

    public long windowMillis() {
        return windowMillis;
    }
}
