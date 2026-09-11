package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Case 候选表 PO（工单 0138 S2）— 幂等键 uk_source_ref (source, source_ref)。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CaseCandidatePO implements Serializable {
    private Long id;
    private String source;
    private String sourceRef;
    private String traceId;
    private String query;
    private String answerSummary;
    private Integer hitDocCount;
    private String toolList;
    private String reason;
    private String status;
    private String promotedDatasetId;
    private String createTime;
}
