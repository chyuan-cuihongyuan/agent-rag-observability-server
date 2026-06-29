package cn.chyuan.ai.observability.api.dto.evaluate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalResultDTO {
    private Long id;
    private String taskId;
    private String traceId;
    private String queryText;
    private String standardAnswer;
    private String actualAnswer;
    private Double recallScore;
    private Double precisionScore;
    private Double f1Score;
    private Double top3HitRate;
    private Double mrrScore;
    private Double ndcgScore;
    private Double mapScore;
    private Double answerSimilarity;
    private Double contextPrecision;
    private Double contextRecall;
    private Double contextRelevance;
    private Double faithfulnessScore;
    private Double relevanceScore;
    private Integer hallucinationFlag;
    private Double completenessScore;
    private Double answerCorrectness;
    private Double overallScore;
    private String evalDetail;
    private String createTime;
    private Double toolSelectionScore;
    private Double toolParamScore;
    private Double toolCallScore;
    private Double intentScore;
    private Double branchScore;
    private Double reasoningScore;
    private Double agentDecisionScore;
}
