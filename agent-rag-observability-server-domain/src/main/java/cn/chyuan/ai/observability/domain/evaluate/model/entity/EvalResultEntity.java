package cn.chyuan.ai.observability.domain.evaluate.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalResultEntity implements Serializable {
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
    private Double answerSimilarity;
    private Double faithfulnessScore;
    private Double relevanceScore;
    private Integer hallucinationFlag;
    private Double completenessScore;
    private Double overallScore;
    private String evalDetail;
    private String createTime;
}
