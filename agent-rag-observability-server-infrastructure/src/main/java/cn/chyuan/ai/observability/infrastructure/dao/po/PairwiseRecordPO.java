package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * pairwise 对局记录表 PO（工单 0170 X1）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PairwiseRecordPO implements Serializable {
    private Long id;
    private String taskA;
    private String taskB;
    private String datasetId;
    private String query;
    private String outcome;
    private Integer pairNo;
    private String createTime;
}
