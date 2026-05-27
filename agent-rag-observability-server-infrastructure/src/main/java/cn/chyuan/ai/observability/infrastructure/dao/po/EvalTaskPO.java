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
}
