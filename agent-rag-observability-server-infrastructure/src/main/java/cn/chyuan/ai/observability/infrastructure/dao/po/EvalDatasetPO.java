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
public class EvalDatasetPO implements Serializable {
    private Long id;
    private String datasetId;
    private String datasetName;
    private String description;
    private Integer itemCount;
    private String itemsJson;
    private String createTime;
    private String updateTime;

    // ========== 新增：版本化 + 三池 + 来源 + 冻结（工单 0134 R2） ==========
    private Integer version;
    private String pool;
    private String source;
    private Integer frozen;
}
