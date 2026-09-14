package cn.chyuan.ai.observability.domain.evaluate.promptopt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 优化实验记录值对象（AO7：MLflow 轻量落点，落 prompt_optimization_experiment 第 32 表）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OptimExperimentVO {

    /** 实验ID */
    private String experimentId;

    /** 实验名称 */
    private String name;

    /** 签名指纹 */
    private String signatureFingerprint;

    /** 数据集指纹 */
    private String datasetFingerprint;

    /** 候选快照 JSON */
    private String candidatesJson;

    /** 得分曲线 JSON（每轮最高分） */
    private String scoreCurveJson;

    /** 胜出提示 */
    private String winner;

    /** 状态：RUNNING / DONE / FAILED */
    private String status;

    /** 创建时间毫秒 */
    private long createdAtMs;

    /** 更新时间毫秒（应用层维护） */
    private long updatedAtMs;
}
