package cn.chyuan.ai.observability.api.dto.evaluate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pairwise 对比请求 DTO（工单 0170 X1）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PairwiseRequestDTO {
    private String taskA;
    private String taskB;
    private String datasetId;
}
