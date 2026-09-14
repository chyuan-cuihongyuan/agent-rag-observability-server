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
    /** 数据集内容快照哈希（Phoenix datasets 思想，版本漂移检测；SELFLOOP3 loop-334，工单 0466/0467） */
    private String datasetContentHash;
    private String createTime;
    private String updateTime;
}
