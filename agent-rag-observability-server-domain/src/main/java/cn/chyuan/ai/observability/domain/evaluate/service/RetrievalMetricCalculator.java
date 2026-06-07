package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RetrievalMetrics;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索类确定性指标计算器 — 纯函数实现，不依赖 LLM。
 * <p>
 * 命中判定：实际检索 chunk 与某条标准 chunk 的词面 Jaccard 相似度 >= 阈值即视为命中，
 * 兼顾分块边界差异（标准 chunk 与实际 chunk 文本不必完全相等）。
 */
@Service
public class RetrievalMetricCalculator {

    /** chunk 命中判定阈值（Jaccard） */
    private static final double HIT_THRESHOLD = 0.5;

    /**
     * 计算检索指标。
     *
     * @param standardChunks 标准 chunk（黄金集，可为空）
     * @param actualChunks   实际检索 chunk（可为空）
     * @param standardAnswer 标准答案（可为空）
     * @param actualAnswer   实际答案（可为空）
     */
    public RetrievalMetrics compute(List<String> standardChunks, List<String> actualChunks,
                                    String standardAnswer, String actualAnswer) {
        List<String> gold = standardChunks == null ? List.of() : standardChunks;
        List<String> actual = actualChunks == null ? List.of() : actualChunks;

        int goldTotal = gold.size();
        int actualTotal = actual.size();

        // 命中的标准 chunk 数（每条标准 chunk 至多计一次）
        int hitGold = 0;
        for (String g : gold) {
            for (String a : actual) {
                if (jaccard(tokenize(g), tokenize(a)) >= HIT_THRESHOLD) {
                    hitGold++;
                    break;
                }
            }
        }

        // 命中标准的实际 chunk 数（用于精确率）
        int hitActual = 0;
        for (String a : actual) {
            for (String g : gold) {
                if (jaccard(tokenize(a), tokenize(g)) >= HIT_THRESHOLD) {
                    hitActual++;
                    break;
                }
            }
        }

        double recall = goldTotal == 0 ? 0.0 : (double) hitGold / goldTotal;
        double precision = actualTotal == 0 ? 0.0 : (double) hitActual / actualTotal;
        double f1 = (recall + precision) == 0 ? 0.0 : 2 * recall * precision / (recall + precision);

        // Top3 命中率：前 3 条实际 chunk 中命中任一标准 chunk 即为 1
        double top3 = 0.0;
        int top3Count = Math.min(3, actualTotal);
        for (int i = 0; i < top3Count; i++) {
            String a = actual.get(i);
            boolean hit = gold.stream().anyMatch(g -> jaccard(tokenize(a), tokenize(g)) >= HIT_THRESHOLD);
            if (hit) {
                top3 = 1.0;
                break;
            }
        }

        double answerSim = jaccard(tokenize(standardAnswer), tokenize(actualAnswer));

        return RetrievalMetrics.builder()
                .recall(round(recall))
                .precision(round(precision))
                .f1(round(f1))
                .top3HitRate(round(top3))
                .answerSimilarity(round(answerSim))
                .build();
    }

    /** 词面 Jaccard 相似度 */
    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }

    /**
     * 简单分词：按非字母数字及中文字符切分英文词，中文按单字切分（无外部分词依赖）。
     */
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        String normalized = text.toLowerCase();
        // 英文/数字词
        List<String> words = new ArrayList<>(Arrays.asList(normalized.split("[^a-z0-9\\u4e00-\\u9fa5]+")));
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            // 中文连续段按单字加入，英文/数字整词加入
            if (w.matches(".*[\\u4e00-\\u9fa5].*")) {
                for (int i = 0; i < w.length(); i++) {
                    char c = w.charAt(i);
                    if (c >= '一' && c <= '龥') {
                        tokens.add(String.valueOf(c));
                    }
                }
            } else {
                tokens.add(w);
            }
        }
        return tokens;
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
