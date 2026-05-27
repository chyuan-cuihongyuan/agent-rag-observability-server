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
}
