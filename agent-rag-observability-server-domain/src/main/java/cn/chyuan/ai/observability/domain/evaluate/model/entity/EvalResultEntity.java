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

    // ========== 新增：工具调用评测字段 ==========
    /** 工具选择正确率 */
    private Double toolSelectionScore;
    /** 工具参数正确率 */
    private Double toolParamScore;
    /** 工具调用综合分 */
    private Double toolCallScore;

    // ========== 新增：Agent 决策评测字段 ==========
    /** 意图识别正确率 */
    private Double intentScore;
    /** 分支选择正确率 */
    private Double branchScore;
    /** 推理质量分 */
    private Double reasoningScore;
    /** Agent 决策综合分 */
    private Double agentDecisionScore;
}
