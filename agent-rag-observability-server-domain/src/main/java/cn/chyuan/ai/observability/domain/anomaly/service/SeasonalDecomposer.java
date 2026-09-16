package cn.chyuan.ai.observability.domain.anomaly.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 季节分解简化（工单 0406 AX2，prophet STL 思想简化）。
 * 序列+周期 p → 按相位均值剖面提取季节分量 → 去季节移动平均得趋势 → 残差=原-趋势-季节；
 * 三分量长度对齐；不足两周期拒绝。纯函数。
 */
public class SeasonalDecomposer {

    /** 分解结果（original 与序列同长） */
    public record Decomposition(java.util.List<Double> original, double[] trend, double[] seasonal, double[] residual, double residualVariance) {
    }

    private final int period;

    public SeasonalDecomposer(int period) {
        if (period < 2) {
            throw new IllegalArgumentException("周期至少为 2");
        }
        this.period = period;
    }

    /**
     * 分解：序列长度 < 2×周期拒绝。
     */
    public Decomposition decompose(List<Double> series) {
        if (series == null || series.size() < 2 * period) {
            throw new IllegalArgumentException("序列长度不足两个周期");
        }
        int n = series.size();
        // 季节剖面：按相位（index % period）均值
        double[] seasonal = new double[n];
        double[] phaseSum = new double[period];
        int[] phaseCount = new int[period];
        for (int i = 0; i < n; i++) {
            int phase = i % period;
            phaseSum[phase] += series.get(i);
            phaseCount[phase]++;
        }
        double overallMean = series.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double[] phaseProfile = new double[period];
        for (int p = 0; p < period; p++) {
            phaseProfile[p] = phaseSum[p] / Math.max(1, phaseCount[p]) - overallMean;
        }
        for (int i = 0; i < n; i++) {
            seasonal[i] = phaseProfile[i % period];
        }
        // 趋势：去季节后移动平均（窗口=周期）
        double[] deseason = new double[n];
        for (int i = 0; i < n; i++) {
            deseason[i] = series.get(i) - phaseProfile[i % period];
        }
        double[] trend = new double[n];
        int half = period / 2;
        for (int i = 0; i < n; i++) {
            int from = Math.max(0, i - half);
            int to = Math.min(n - 1, i + half);
            double sum = 0;
            for (int j = from; j <= to; j++) {
                sum += deseason[j];
            }
            trend[i] = sum / (to - from + 1);
        }
        // 残差
        double[] residual = new double[n];
        double sq = 0;
        for (int i = 0; i < n; i++) {
            residual[i] = series.get(i) - trend[i] - phaseProfile[i % period];
            sq += residual[i] * residual[i];
        }
        return new Decomposition(toList(series), trend, seasonal, residual, sq / n);
    }

    private List<Double> toList(List<Double> series) {
        return new ArrayList<>(series);
    }
}
