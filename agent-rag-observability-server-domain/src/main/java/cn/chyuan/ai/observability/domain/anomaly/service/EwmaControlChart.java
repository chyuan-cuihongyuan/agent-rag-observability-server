package cn.chyuan.ai.observability.domain.anomaly.service;

import java.util.ArrayList;
import java.util.List;

/**
 * EWMA 控制图（工单 0405 AX1，经典 SPC/Kapacitor 思想）。
 * 指标序列 → 指数加权移动均值±kσ 控制限 → 逐点超限判定（ALARM 侧别 UP/DOWN）；
 * 预热期（前 warmup 点不判定）；纯函数。
 */
public class EwmaControlChart {

    /** 判定 */
    public enum Verdict {
        NORMAL, UP, DOWN
    }

    /** 单点判定结果 */
    public record PointVerdict(int index, double value, double ewma, double upperLimit, double lowerLimit, Verdict verdict) {
    }

    private final double alpha;
    private final double kSigma;
    private final int warmup;

    public EwmaControlChart(double alpha, double kSigma, int warmup) {
        if (alpha <= 0 || alpha > 1) {
            throw new IllegalArgumentException("平滑系数须在 (0,1]");
        }
        if (kSigma <= 0) {
            throw new IllegalArgumentException("控制限系数必须为正");
        }
        this.alpha = alpha;
        this.kSigma = kSigma;
        this.warmup = Math.max(0, warmup);
    }

    /**
     * 逐点判定：EWMA 递推，控制限=EWMA±k·σ（σ 为序列标准差，预热期后判定）。
     */
    public List<PointVerdict> judge(List<Double> series) {
        List<PointVerdict> out = new ArrayList<>();
        if (series == null || series.isEmpty()) {
            return out;
        }
        double ewma = series.get(0);
        double sigma = sigma(series);
        double upper = ewma + kSigma * sigma;
        double lower = ewma - kSigma * sigma;
        for (int i = 0; i < series.size(); i++) {
            double value = series.get(i);
            if (i > 0) {
                ewma = alpha * value + (1 - alpha) * ewma;
                upper = ewma + kSigma * sigma;
                lower = ewma - kSigma * sigma;
            }
            Verdict verdict = Verdict.NORMAL;
            if (i >= warmup) {
                if (value > upper) {
                    verdict = Verdict.UP;
                } else if (value < lower) {
                    verdict = Verdict.DOWN;
                }
            }
            out.add(new PointVerdict(i, value, ewma, upper, lower, verdict));
        }
        return out;
    }

    /** 样本标准差 */
    private double sigma(List<Double> series) {
        double mean = series.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = series.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        return Math.sqrt(variance);
    }
}
