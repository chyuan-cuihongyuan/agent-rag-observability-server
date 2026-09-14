package cn.chyuan.ai.observability.domain.evaluate.promptopt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提示发布建议值对象（AO8：胜出提示 → 发布建议，状态机）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublishSuggestionVO {

    /** 状态常量 */
    public static final String PROPOSED = "PROPOSED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";

    /** 建议ID */
    private String suggestionId;

    /** 来源实验ID */
    private String experimentId;

    /** 胜出提示 */
    private String prompt;

    /** 基线提示 */
    private String baselinePrompt;

    /** 提升幅度（胜出均值-基线均值） */
    private double lift;

    /** 数据集指纹 */
    private String datasetFingerprint;

    /** 风险备注 */
    private String note;

    /** 状态：PROPOSED → ACCEPTED / REJECTED（终态） */
    private String status;

    /** 提出时间毫秒 */
    private long proposedAtMs;

    /** 决策时间毫秒（未决策为 0） */
    private long decidedAtMs;
}
