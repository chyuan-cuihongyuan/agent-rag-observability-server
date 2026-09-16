package cn.chyuan.ai.observability.domain.anomaly.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LOF 多维离群检测（工单 0410 AX6，pyod 思想）。
 * 多维指标点集 → k 近邻 → 局部可达密度 → LOF 因子（>阈值判 OUTLIER 并给分）；
 * 维度 z-score 归一化可配；k 越界（≥点数）降级为 k=n-1。纯函数。
 */
public class LofOutlierDetector {

    /** 检测结果点 */
    public record ScoredPoint(int index, double lof, boolean outlier) {
    }

    private final int k;
    private final double lofThreshold;
    private final boolean normalize;

    public LofOutlierDetector(int k, double lofThreshold, boolean normalize) {
        if (k < 1) {
            throw new IllegalArgumentException("k 至少为 1");
        }
        if (lofThreshold <= 1.0) {
            throw new IllegalArgumentException("LOF 阈值必须大于 1.0");
        }
        this.k = k;
        this.lofThreshold = lofThreshold;
        this.normalize = normalize;
    }

    /**
     * 检测：k 越界降级 k=n-1；点维度一致校验。
     */
    public List<ScoredPoint> detect(List<double[]> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("点集至少 2 个");
        }
        int n = points.size();
        int dim = points.get(0).length;
        for (double[] point : points) {
            if (point.length != dim) {
                throw new IllegalArgumentException("点维度不一致");
            }
        }
        double[][] data = normalize ? zScore(points) : toArray(points);
        int kk = Math.min(k, n - 1); // k 越界降级
        double[][] dist = distanceMatrix(data);
        // k 距离与 k 邻域
        double[] kDist = new double[n];
        List<List<Integer>> neighbors = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Integer[] order = new Integer[n];
            for (int j = 0; j < n; j++) {
                order[j] = j;
            }
            final int fi = i;
            Arrays.sort(order, (a, b) -> Double.compare(dist[fi][a], dist[fi][b]));
            List<Integer> knn = new ArrayList<>();
            for (int j = 1; j <= kk; j++) {
                knn.add(order[j]);
            }
            kDist[i] = dist[i][order[kk]];
            neighbors.add(knn);
        }
        // 可达距离与局部可达密度
        double[] lrd = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j : neighbors.get(i)) {
                sum += reachDist(i, j, dist, kDist);
            }
            lrd[i] = sum / Math.max(1, neighbors.get(i).size());
            lrd[i] = neighbors.get(i).isEmpty() || sum == 0 ? Double.MAX_VALUE : 1.0 / (sum / neighbors.get(i).size());
        }
        // LOF
        List<ScoredPoint> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double ratio = 0;
            for (int j : neighbors.get(i)) {
                double lrdJ = Double.isInfinite(lrd[j]) ? Double.MAX_VALUE : lrd[j];
                double lrdI = Double.isInfinite(lrd[i]) ? Double.MAX_VALUE : lrd[i];
                ratio += lrdJ / Math.max(1e-12, lrdI == Double.MAX_VALUE ? Double.MAX_VALUE : lrdI);
            }
            double lof = ratio / Math.max(1, neighbors.get(i).size());
            out.add(new ScoredPoint(i, round(lof), lof > lofThreshold));
        }
        return out;
    }

    private double reachDist(int i, int j, double[][] dist, double[] kDist) {
        return Math.max(kDist[j], dist[i][j]);
    }

    private double[][] toArray(List<double[]> points) {
        return points.toArray(new double[0][]);
    }

    /** 维度 z-score 归一化 */
    private double[][] zScore(List<double[]> points) {
        int n = points.size();
        int dim = points.get(0).length;
        double[][] out = new double[n][dim];
        for (int d = 0; d < dim; d++) {
            double mean = 0;
            for (double[] point : points) {
                mean += point[d];
            }
            mean /= n;
            double variance = 0;
            for (double[] point : points) {
                variance += (point[d] - mean) * (point[d] - mean);
            }
            double sigma = Math.sqrt(variance / Math.max(1, n - 1));
            for (int i = 0; i < n; i++) {
                out[i][d] = sigma == 0 ? 0 : (points.get(i)[d] - mean) / sigma;
            }
        }
        return out;
    }

    private double[][] distanceMatrix(double[][] data) {
        int n = data.length;
        double[][] dist = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double sq = 0;
                for (int d = 0; d < data[i].length; d++) {
                    sq += (data[i][d] - data[j][d]) * (data[i][d] - data[j][d]);
                }
                dist[i][j] = Math.sqrt(sq);
                dist[j][i] = dist[i][j];
            }
        }
        return dist;
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
