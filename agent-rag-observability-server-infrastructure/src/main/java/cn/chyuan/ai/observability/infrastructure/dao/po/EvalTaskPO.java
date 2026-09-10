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
public class EvalTaskPO implements Serializable {
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
    /** 试验次数 k（默认 1 保持旧行为） */
    private Integer trials;
    /** 样本达标阈值（NULL=应用默认 0.5） */
    private Double passThreshold;
    /** 通过率 Pass@k（完成时回写） */
    private Double passRate;
    /** per-trial 综合分标准差（完成时回写） */
    private Double scoreStdDev;
    /** 绑定的门禁规则 ID（回测任务） */
    private String gateId;
}
