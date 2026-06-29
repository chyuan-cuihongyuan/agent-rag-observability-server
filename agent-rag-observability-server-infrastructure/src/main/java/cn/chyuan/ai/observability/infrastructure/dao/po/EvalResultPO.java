package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalResultPO implements Serializable {
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

    // ========== 工具调用评测分项（修复断层：原仅写 eval_detail JSON，现结构化落库） ==========
    private Double toolSelectionScore;
    private Double toolParamScore;
    private Double toolCallScore;

    // ========== Agent 决策评测分项（修复断层：原仅写 eval_detail JSON，现结构化落库） ==========
    private Double intentScore;
    private Double branchScore;
    private Double reasoningScore;
    private Double agentDecisionScore;
}
