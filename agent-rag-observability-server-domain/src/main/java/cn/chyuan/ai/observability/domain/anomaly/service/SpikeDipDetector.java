package cn.chyuan.ai.observability.domain.anomaly.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 尖峰与凹陷检测（工单 0408 AX4）。
 * 序列 → 局部极值点 → 邻域（前后 w 窗）中位数倍率≥阈值 → SPIKE（正向）/DIP（负向）标注；
 * 连续同向极值取幅度最大；窗口与倍率可配。纯函数。
 */
public class SpikeDipDetector {

    /** 标注类型 */
    public static final String SPIKE = "SPIKE";
    public static final String DIP = "DIP";

    /** 异常点标注 */
    public record AnomalyPoint(int index, double value, String type, double ratio) {
    }

    private final int window;
    private final double ratioThreshold;

    public SpikeDipDetector(int window, double ratioThreshold) {
        if (window < 1) {
            throw new IllegalArgumentException("邻域窗口至少 1");
        }
        if (ratioThreshold <= 1.0) {
            throw new IllegalArgumentException("倍率阈值必须大于 1.0");
        }
        this.window = window;
        this.ratioThreshold = ratioThreshold;
    }

    /**
     * 检测：局部极值（严格大于/小于两侧邻居）且相对邻域中位数倍率≥阈值。
     */
    public List<AnomalyPoint> detect(List<Double> series) {
        List<AnomalyPoint> out = new ArrayList<>();
        int n = series == null ? 0 : series.size();
        for (int i = 1; i < n - 1; i++) {
            double value = series.get(i);
            boolean isPeak = value > series.get(i - 1) && value > series.get(i + 1);
            boolean isValley = value < series.get(i - 1) && value < series.get(i + 1);
            if (!isPeak && !isValley) {
                continue;
            }
            double median = neighborhoodMedian(series, i);
            if (median <= 0) {
                continue;
            }
            double ratio = value / median;
            if (isPeak && ratio >= ratioThreshold) {
                out.add(new AnomalyPoint(i, value, SPIKE, round(ratio)));
            } else if (isValley && (1.0 / ratio) >= ratioThreshold) {
                out.add(new AnomalyPoint(i, value, DIP, round(1.0 / ratio)));
            }
        }
        // 连续同向极值取幅度最大
        return keepLargestPerRun(out, series);
    }

    /** 邻域（前后 w 窗，不含自身）中位数 */
    private double neighborhoodMedian(List<Double> series, int center) {
        List<Double> neighbors = new ArrayList<>();
        for (int j = Math.max(0, center - window); j <= Math.min(series.size() - 1, center + window); j++) {
            if (j != center) {
                neighbors.add(series.get(j));
            }
        }
        if (neighbors.isEmpty()) {
            return 0;
        }
        neighbors.sort(Double::compareTo);
        return neighbors.get(neighbors.size() / 2);
    }

    /** 连续同向（相邻索引连续且同型）标注保留幅度最大者 */
    private List<AnomalyPoint> keepLargestPerRun(List<AnomalyPoint> points, List<Double> series) {
        List<AnomalyPoint> out = new ArrayList<>();
        for (AnomalyPoint point : points) {
            if (!out.isEmpty()) {
                AnomalyPoint last = out.get(out.size() - 1);
                if (last.type().equals(point.type()) && point.index() - last.index() <= window) {
                    if (amplitude(point) > amplitude(last)) {
                        out.set(out.size() - 1, point);
                    }
                    continue;
                }
            }
            out.add(point);
        }
        return out;
    }

    private double amplitude(AnomalyPoint point) {
        return point.type().equals(SPIKE) ? point.value() : -point.value();
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
