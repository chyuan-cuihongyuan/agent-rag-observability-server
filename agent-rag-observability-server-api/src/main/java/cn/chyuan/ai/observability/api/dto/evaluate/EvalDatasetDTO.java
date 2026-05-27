package cn.chyuan.ai.observability.api.dto.evaluate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalDatasetDTO {
    private String datasetId;
    private String datasetName;
    private String description;
    private Integer itemCount;
    private String itemsJson;
    private String createTime;
    private String updateTime;
}
