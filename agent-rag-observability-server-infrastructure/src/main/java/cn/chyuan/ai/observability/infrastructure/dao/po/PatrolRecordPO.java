package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 巡检拨测记录表 PO（工单 0137 S1）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatrolRecordPO implements Serializable {
    private Long id;
    private String roundId;
    private String taskRef;
    private String query;
    private String agentId;
    private String status;
    private Double score;
    private Long durationMs;
    private String errorSummary;
    private String traceId;
    private String createTime;
}
