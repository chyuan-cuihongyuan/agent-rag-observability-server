package cn.chyuan.ai.observability.domain.lineage.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产健康分（工单 0290 AK6）—
 * 新鲜度分：距最近血缘完成事件时长按指数衰减（半衰期可配）；
 * 质量分：联动 AD2 质量断言最近通过率；加权合成分 + A/B/C/D 分级（权重可配钳制）。
 * 无质量数据时退化只用新鲜度。快照环形留档。
 */
public class AssetHealthCalculator {

    public static final String GRADE_A = "A";
    public static final String GRADE_B = "B";
    public static final String GRADE_C = "C";
    public static final String GRADE_D = "D";

    /** 健康输入 */
    public record HealthInput(String urn, long lastCompletedMs, long nowMs,
            double qualityPassRate, boolean hasQualityData) {
    }

    /** 健康快照 */
    public record AssetHealth(String urn, double freshnessScore, double qualityScore,
            double totalScore, String grade, long computedAtMs) {
    }

    /** 质量断言通过率端口（联动 AD2 QualityRunner 结果；无数据返回空） */
    public interface QualityRatePort {

        /** 最近通过率（0-1），无数据返回 null */
        Double recentPassRate(String assetUrn);
    }

    private final long halfLifeMs;
    private final double freshnessWeight;

    public AssetHealthCalculator(long halfLifeMs, double freshnessWeight) {
        this.halfLifeMs = Math.max(1, halfLifeMs);
        this.freshnessWeight = Math.min(1.0, Math.max(0.0, freshnessWeight));
    }

    /** 新鲜度分：2^(-时长/半衰期) */
    public static double freshnessScore(long lastCompletedMs, long nowMs, long halfLifeMs) {
        long age = Math.max(0, nowMs - lastCompletedMs);
        return Math.pow(0.5, (double) age / halfLifeMs);
    }

    /** 单资产健康分（无质量数据退化只用新鲜度） */
    public AssetHealth compute(HealthInput input) {
        double freshness = freshnessScore(input.lastCompletedMs(), input.nowMs(), halfLifeMs);
        double quality = input.hasQualityData() ? Math.min(1.0, Math.max(0.0, input.qualityPassRate())) : -1;
        double total;
        if (quality < 0) {
            total = freshness;
        } else {
            total = freshnessWeight * freshness + (1 - freshnessWeight) * quality;
        }
        return new AssetHealth(input.urn(), round(freshness), round(quality < 0 ? 0 : quality),
                round(total), grade(total), input.nowMs());
    }

    /** 分级：≥0.75=A，≥0.5=B，≥0.25=C，否则 D */
    public static String grade(double score) {
        if (score >= 0.75) {
            return GRADE_A;
        }
        if (score >= 0.5) {
            return GRADE_B;
        }
        if (score >= 0.25) {
            return GRADE_C;
        }
        return GRADE_D;
    }

    /** 批量计算（排序按总分倒序） */
    public List<AssetHealth> computeAll(List<HealthInput> inputs) {
        List<AssetHealth> out = new ArrayList<>();
        for (HealthInput input : inputs) {
            out.add(compute(input));
        }
        out.sort((a, b) -> Double.compare(b.totalScore(), a.totalScore()));
        return out;
    }

    /** 健康快照环形留档服务 */
    public static class AssetHealthStore {
        private final Map<String, Deque<AssetHealth>> snapshots = new java.util.concurrent.ConcurrentHashMap<>();
        private static final int MAX_PER_ASSET = 50;

        public void record(AssetHealth health) {
            Deque<AssetHealth> deque = snapshots.computeIfAbsent(health.urn(), key -> new ArrayDeque<>());
            synchronized (deque) {
                deque.addLast(health);
                while (deque.size() > MAX_PER_ASSET) {
                    deque.removeFirst();
                }
            }
        }

        public AssetHealth latest(String urn) {
            Deque<AssetHealth> deque = snapshots.get(urn);
            synchronized (deque == null ? new Object() : deque) {
                return deque == null ? null : deque.peekLast();
            }
        }

        public Map<String, AssetHealth> latestAll() {
            Map<String, AssetHealth> out = new LinkedHashMap<>();
            snapshots.keySet().stream().sorted().forEach(urn -> {
                AssetHealth health = latest(urn);
                if (health != null) {
                    out.put(urn, health);
                }
            });
            return out;
        }
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
