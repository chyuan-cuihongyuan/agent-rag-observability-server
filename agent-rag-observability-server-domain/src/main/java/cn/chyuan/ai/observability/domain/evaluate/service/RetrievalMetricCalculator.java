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
 * <p>
 * 覆盖两类指标：
 * <ul>
 *   <li>二值命中式（传统 IR）：recall / precision / f1 / top3HitRate</li>
 *   <li>排序感知式（位置加权）：mrr / ndcg / map —— 奖励命中条排在更靠前位置</li>
 * </ul>
 */
@Service
public class RetrievalMetricCalculator {

    /** chunk 命中判定阈值（Jaccard） */
    private static final double HIT_THRESHOLD = 0.5;
    /** NDCG 位置折损的分母基数：增益 = rel_i / log2(rank + 1)，rank 从 1 起算 */
    private static final double LOG_BASE = 2.0;

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

        // 预计算每个实际 chunk 的相关性向量：relevance[i]=1 表示第 i 条实际 chunk 命中任一标准 chunk。
        // 这是所有排序感知指标（MRR/NDCG/MAP）与二值命中指标的共同基础，只算一次。
        boolean[] relevant = new boolean[actualTotal];
        for (int i = 0; i < actualTotal; i++) {
            relevant[i] = isHit(actual.get(i), gold);
        }
        // 命中的标准 chunk 数（每条标准 chunk 至多计一次）——用于 recall
        int hitGold = 0;
        for (String g : gold) {
            for (String a : actual) {
                if (jaccard(tokenize(g), tokenize(a)) >= HIT_THRESHOLD) {
                    hitGold++;
                    break;
                }
            }
        }
        // 命中标准的实际 chunk 数（用于精确率）——复用 relevant 向量
        int hitActual = 0;
        for (boolean r : relevant) {
            if (r) hitActual++;
        }

        double recall = goldTotal == 0 ? 0.0 : (double) hitGold / goldTotal;
        double precision = actualTotal == 0 ? 0.0 : (double) hitActual / actualTotal;
        double f1 = (recall + precision) == 0 ? 0.0 : 2 * recall * precision / (recall + precision);

        // Top3 命中率：前 3 条实际 chunk 中命中任一标准 chunk 即为 1
        double top3 = 0.0;
        int top3Count = Math.min(3, actualTotal);
        for (int i = 0; i < top3Count; i++) {
            if (relevant[i]) {
                top3 = 1.0;
                break;
            }
        }

        // ===== 排序感知指标（位置加权） =====
        double mrr = computeMrr(relevant);
        double ndcg = computeNdcg(relevant);
        double map = computeMap(relevant, hitGold);

        double answerSim = jaccard(tokenize(standardAnswer), tokenize(actualAnswer));

        return RetrievalMetrics.builder()
                .recall(round(recall))
                .precision(round(precision))
                .f1(round(f1))
                .top3HitRate(round(top3))
                .mrr(round(mrr))
                .ndcg(round(ndcg))
                .map(round(map))
                .answerSimilarity(round(answerSim))
                .build();
    }

    /** 判断一条实际 chunk 是否命中任一标准 chunk（Jaccard >= 阈值） */
    private boolean isHit(String actualChunk, List<String> gold) {
        Set<String> actualTokens = tokenize(actualChunk);
        for (String g : gold) {
            if (jaccard(actualTokens, tokenize(g)) >= HIT_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * MRR 平均倒数排名：第一条命中的实际 chunk 的位置倒数（1/rank，rank 从 1 起算），无命中=0。
     * 例：命中条排第 1 → 1.0；排第 3 → 0.333。
     */
    private double computeMrr(boolean[] relevant) {
        for (int i = 0; i < relevant.length; i++) {
            if (relevant[i]) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * NDCG 归一化折损累计增益：DCG/iDCG。
     * rel_i ∈ {0,1}；DCG = Σ rel_i / log2(i+2)（i 为 0-based，对应 rank i+1 的折损 log2(rank+1)）。
     * iDCG = 理想排序下相同数量相关项的 DCG（全排前面）。
     */
    private double computeNdcg(boolean[] relevant) {
        int n = relevant.length;
        int relCount = 0;
        double dcg = 0.0;
        for (int i = 0; i < n; i++) {
            if (relevant[i]) {
                relCount++;
                dcg += 1.0 / (Math.log(i + 2) / Math.log(LOG_BASE));
            }
        }
        if (relCount == 0) {
            return 0.0;
        }
        // iDCG：relCount 个相关项全部排最前
        double idcg = 0.0;
        for (int k = 0; k < relCount; k++) {
            idcg += 1.0 / (Math.log(k + 2) / Math.log(LOG_BASE));
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    /**
     * MAP 平均精度均值：在每个命中位置累计 precision@k，求和后除以总相关数（命中标准 chunk 数）。
     * 例：relevant=[1,0,1]，hitGold=2 → precision@1=1/1, precision@3=2/3 → MAP=(1.0+0.6667)/2=0.8333。
     * 无相关项时返回 0。
     */
    private double computeMap(boolean[] relevant, int totalRelevant) {
        if (totalRelevant == 0) {
            return 0.0;
        }
        double sumPrecision = 0.0;
        int hitsSoFar = 0;
        for (int i = 0; i < relevant.length; i++) {
            if (relevant[i]) {
                hitsSoFar++;
                sumPrecision += (double) hitsSoFar / (i + 1);
            }
        }
        return sumPrecision / totalRelevant;
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
