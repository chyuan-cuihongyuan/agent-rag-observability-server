package cn.chyuan.ai.observability.domain.evaluate.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pairwise 对局记录实体（工单 0170 X1）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PairwiseRecordEntity {

    private Long id;
    private String taskA;
    private String taskB;
    private String datasetId;
    private String query;
    /** A_WIN / B_WIN / TIE */
    private String outcome;
    /** 对局序号（同一对任务内的第几题） */
    private Integer pairNo;
    private String createTime;
}
