package cn.chyuan.ai.observability.domain.errorkernel.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 相似聚合（工单 0698 CE4，sentry 思想）。
 * 无指纹兜底：消息 token 相似度（Jaccard）阈值并入近邻组/
 * 组数上限与溢出新建/相似阈值可配。
 */
public final class SimilarityGrouper {

    private final double threshold;
    private final int maxGroups;

    public SimilarityGrouper(double threshold, int maxGroups) {
        if (threshold <= 0 || threshold > 1) {
            throw new IllegalArgumentException("相似阈值须在 (0,1]");
        }
        if (maxGroups <= 0) {
            throw new IllegalArgumentException("组数上限必须为正");
        }
        this.threshold = threshold;
        this.maxGroups = maxGroups;
    }

    /** token 集合（小写、按非字母数字切分、去空） */
    public static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        for (String token : text.toLowerCase().split("[^a-z0-9\\p{IsIdeographic}]+")) {
            if (!token.isBlank()) {
                out.add(token);
            }
        }
        return out;
    }

    /** Jaccard 相似度 */
    public static double similarity(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0;
        }
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        Set<String> intersection = new HashSet<>(ta);
        intersection.retainAll(tb);
        return (double) intersection.size() / union.size();
    }

    /**
     * 兜底归属：在候选组标题中找相似度最高者；
     * 达到阈值并入（返回组键），未达阈值且未达组数上限则新建（返回 null 由调用方建组），
     * 达上限则强制并入最接近者。
     */
    public String assignNearest(String title, Map<String, String> existingTitles, int existingGroupCount) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("标题不得为空");
        }
        String bestKey = null;
        double bestScore = 0;
        String firstKey = null;
        for (Map.Entry<String, String> e : existingTitles.entrySet()) {
            if (firstKey == null) {
                firstKey = e.getKey();
            }
            double score = similarity(title, e.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestKey = e.getKey();
            }
        }
        if (bestScore >= threshold) {
            return bestKey;
        }
        if (existingGroupCount < maxGroups) {
            return null;
        }
        // 组数达上限强制并入：优先最接近组，全零相似时兜底到首组
        return bestKey != null ? bestKey : firstKey;
    }

    /** 相似度矩阵（诊断视图） */
    public static double[][] matrix(List<String> titles) {
        double[][] out = new double[titles.size()][titles.size()];
        for (int i = 0; i < titles.size(); i++) {
            for (int j = 0; j < titles.size(); j++) {
                out[i][j] = similarity(titles.get(i), titles.get(j));
            }
        }
        return out;
    }
}
