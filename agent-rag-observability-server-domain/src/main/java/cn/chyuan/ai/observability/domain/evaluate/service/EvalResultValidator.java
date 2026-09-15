package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 评测结果分值域校验器（SELFLOOP4 loop-425，工单 0648/0649）。
 * <p>
 * 口径（F4 指标分层口径文档 / loop-319）：全部分值 ∈ [0,1]，4 位小数；
 * verdict 缺席时 context/正确性系列允许 null（RAG_RETRIEVAL 类型常态）。
 * 校验为观测型门禁：违规由调用方 warn 留痕，不阻断落库——数据质量问题需要
 * 被看见，而非被丢弃。
 */
public final class EvalResultValidator {

    private EvalResultValidator() {
    }

    /**
     * 返回违规清单；空清单 = 合法。
     */
    public static List<String> violations(EvalResultEntity r) {
        List<String> problems = new ArrayList<>();
        if (r.getTaskId() == null || r.getTaskId().isBlank()) {
            problems.add("taskId 缺失");
        }
        if (r.getOverallScore() == null) {
            problems.add("overallScore 缺失");
        } else if (outOfRange(r.getOverallScore())) {
            problems.add("overallScore 越界: " + r.getOverallScore());
        }
        if (r.getHallucinationFlag() != null && r.getHallucinationFlag() != 0
                && r.getHallucinationFlag() != 1) {
            problems.add("hallucinationFlag 非法: " + r.getHallucinationFlag());
        }

        Map<String, Function<EvalResultEntity, Double>> scores = Map.ofEntries(
                Map.entry("recallScore", EvalResultEntity::getRecallScore),
                Map.entry("precisionScore", EvalResultEntity::getPrecisionScore),
                Map.entry("f1Score", EvalResultEntity::getF1Score),
                Map.entry("top3HitRate", EvalResultEntity::getTop3HitRate),
                Map.entry("mrrScore", EvalResultEntity::getMrrScore),
                Map.entry("ndcgScore", EvalResultEntity::getNdcgScore),
                Map.entry("mapScore", EvalResultEntity::getMapScore),
                Map.entry("answerSimilarity", EvalResultEntity::getAnswerSimilarity),
                Map.entry("contextPrecision", EvalResultEntity::getContextPrecision),
                Map.entry("contextRecall", EvalResultEntity::getContextRecall),
                Map.entry("contextRelevance", EvalResultEntity::getContextRelevance),
                Map.entry("faithfulnessScore", EvalResultEntity::getFaithfulnessScore),
                Map.entry("relevanceScore", EvalResultEntity::getRelevanceScore),
                Map.entry("completenessScore", EvalResultEntity::getCompletenessScore),
                Map.entry("answerCorrectness", EvalResultEntity::getAnswerCorrectness),
                Map.entry("toolSelectionScore", EvalResultEntity::getToolSelectionScore),
                Map.entry("toolParamScore", EvalResultEntity::getToolParamScore),
                Map.entry("toolCallScore", EvalResultEntity::getToolCallScore),
                Map.entry("intentScore", EvalResultEntity::getIntentScore),
                Map.entry("branchScore", EvalResultEntity::getBranchScore),
                Map.entry("reasoningScore", EvalResultEntity::getReasoningScore),
                Map.entry("agentDecisionScore", EvalResultEntity::getAgentDecisionScore));
        for (Map.Entry<String, Function<EvalResultEntity, Double>> e : scores.entrySet()) {
            Double v = e.getValue().apply(r);
            if (v != null && outOfRange(v)) {
                problems.add(e.getKey() + " 越界: " + v);
            }
        }
        return problems;
    }

    private static boolean outOfRange(double v) {
        return v < 0.0 || v > 1.0;
    }
}
