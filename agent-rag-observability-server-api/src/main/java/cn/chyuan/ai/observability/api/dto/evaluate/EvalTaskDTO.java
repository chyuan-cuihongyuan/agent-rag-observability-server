package cn.chyuan.ai.observability.api.dto.evaluate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalTaskDTO {
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
    /** 试验次数 k（创建任务可传，默认 1；LLM judge 调用与耗时随 k 线性放大） */
    private Integer trials;
    /** 样本达标阈值（创建任务可传，默认 0.5 沿用 Rubric 达标线） */
    private Double passThreshold;
    /** 通过率 Pass@k（任务完成后回填） */
    private Double passRate;
    /** per-trial 综合分标准差（任务完成后回填） */
    private Double scoreStdDev;
    /** 绑定的门禁规则 ID（回测任务回填） */
    private String gateId;
}
