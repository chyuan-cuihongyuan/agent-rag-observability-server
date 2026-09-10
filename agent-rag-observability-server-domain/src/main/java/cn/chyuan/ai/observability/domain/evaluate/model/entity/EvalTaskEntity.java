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
public class EvalTaskEntity implements Serializable {
    private Long id;
    private String taskId;
    private String taskName;
    private String evalType;
    private String datasetId;
    private String status;
    private Integer totalCount;
    private Integer completedCount;
    private String modelVersion;
    private String ragStrategyVersion;
    private Double avgOverallScore;
    private String createTime;
    private String updateTime;

    // ========== 新增：Pass@k 多试验 + 回测门禁（工单 0135 R3 / 0136 R4） ==========
    /** 试验次数 k（默认 1 保持旧行为；k>1 时每 trial 独立走答案源） */
    private Integer trials;
    /** 样本达标阈值（NULL=应用默认 0.5，沿用 Rubric 引擎达标线） */
    private Double passThreshold;
    /** 通过率 Pass@k（k 次 trial 至少 1 次达标的样本占比，任务完成时回写） */
    private Double passRate;
    /** per-trial 综合分均值的标准差（任务完成时回写；k=1 恒为 0） */
    private Double scoreStdDev;
    /** 绑定的门禁规则 ID（回测任务非空；任务完成后回调门禁判定） */
    private String gateId;
}
