package cn.chyuan.ai.observability.api.dto.patrol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 巡检拨测记录 DTO（工单 0137 S1）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatrolRecordDTO {
    private Long id;
    /** 轮次 ID */
    private String roundId;
    /** 种子任务标识 */
    private String taskRef;
    /** 拨测查询原文 */
    private String query;
    /** 目标智能体 ID */
    private String agentId;
    /** 结果三态：SUCCESS / FAIL / TIMEOUT */
    private String status;
    /** 轻量质量分（0-1，可为空） */
    private Double score;
    /** 拨测耗时（毫秒） */
    private Long durationMs;
    /** 错误摘要 */
    private String errorSummary;
    /** 关联 traceId */
    private String traceId;
    private String createTime;
}
