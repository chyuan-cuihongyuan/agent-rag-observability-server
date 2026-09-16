package cn.chyuan.ai.observability.domain.anomaly.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 预测基线（工单 0409 AX5，prophet 预测思想简化）。
 * 序列 → 近窗移动平均锚点 + 最小二乘线性趋势 → 未来 h 步外推 + 预测区间（残差σ×z 系数可配）；
 * 趋势不显著（斜率绝对值低于阈值）退化为水平基线。纯函数。
 */
public class ForecastBaseline {

    /** 预测结果：各步点值与区间 */
    public record Forecast(List<Double> point, List<Double> upper, List<Double> lower, boolean flatDegenerate) {
    }

    private final int anchorWindow;
    private final double zCoefficient;
    private final double slopeSignificance;

    public ForecastBaseline(int anchorWindow, double zCoefficient, double slopeSignificance) {
        if (anchorWindow < 2) {
            throw new IllegalArgumentException("移动平均锚窗至少 2");
        }
        if (zCoefficient <= 0 || slopeSignificance < 0) {
            throw new IllegalArgumentException("z 系数必须为正、斜率显著性阈值不可为负");
        }
        this.anchorWindow = anchorWindow;
        this.zCoefficient = zCoefficient;
        this.slopeSignificance = slopeSignificance;
    }

    /**
     * 外推 h 步：锚点=近窗移动平均末端；趋势=末端窗内最小二乘斜率；区间=残差σ×z。
     */
    public Forecast forecast(List<Double> series, int horizon) {
        if (series == null || series.size() < anchorWindow || horizon < 1) {
            throw new IllegalArgumentException("序列不足锚窗或步数为 0");
        }
        int n = series.size();
        // 近窗线性拟合（x=0..w-1）
        int w = Math.min(anchorWindow * 2, n);
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        for (int i = 0; i < w; i++) {
            double x = i;
            double y = series.get(n - w + i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denom = w * sumXX - sumX * sumX;
        double slope = denom == 0 ? 0 : (w * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / w;
        boolean flat = Math.abs(slope) < slopeSignificance;
        if (flat) {
            slope = 0;
            intercept = series.subList(n - anchorWindow, n).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
        }
        // 残差σ（拟合段）
        double sq = 0;
        for (int i = 0; i < w; i++) {
            double fitted = intercept + slope * i;
            double actual = series.get(n - w + i);
            sq += (actual - fitted) * (actual - fitted);
        }
        double sigma = Math.sqrt(sq / Math.max(1, w - 2));
        List<Double> point = new ArrayList<>();
        List<Double> upper = new ArrayList<>();
        List<Double> lower = new ArrayList<>();
        for (int step = 1; step <= horizon; step++) {
            double x = w - 1 + step;
            double value = intercept + slope * x;
            double band = zCoefficient * sigma;
            point.add(round(value));
            upper.add(round(value + band));
            lower.add(round(value - band));
        }
        return new Forecast(point, upper, lower, flat);
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
