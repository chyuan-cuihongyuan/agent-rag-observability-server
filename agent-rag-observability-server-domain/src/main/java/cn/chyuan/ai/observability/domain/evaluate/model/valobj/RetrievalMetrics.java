package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索类确定性指标 — 由 RetrievalMetricCalculator 基于标准 chunk 与实际 chunk 纯计算得出，不依赖 LLM。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RetrievalMetrics {
    /** 召回率：命中标准 chunk 数 / 标准 chunk 总数 */
    private double recall;
    /** 精确率：命中标准 chunk 数 / 实际检索 chunk 数 */
    private double precision;
    /** F1：召回率与精确率的调和平均 */
    private double f1;
    /** Top3 命中率：前 3 条实际 chunk 命中任一标准 chunk 则为 1 */
    private double top3HitRate;
    /** 答案词面相似度（Jaccard），无 LLM 时作为语义相似度兜底 */
    private double answerSimilarity;
}
