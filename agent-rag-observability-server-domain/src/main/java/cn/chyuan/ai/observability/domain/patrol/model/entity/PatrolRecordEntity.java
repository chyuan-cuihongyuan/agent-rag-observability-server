package cn.chyuan.ai.observability.domain.patrol.model.entity;

import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 巡检拨测记录实体（工单 0137 S1）— 一轮拨测中的一个种子任务的单次结果。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatrolRecordEntity {

    private Long id;
    /** 轮次 ID（同轮共享，用于 /patrol/latest 聚最近一轮） */
    private String roundId;
    /** 种子任务标识（固定查询集为序号编号；golden 池为条目 query 截断） */
    private String taskRef;
    /** 拨测查询原文 */
    private String query;
    /** 目标智能体 ID */
    private String agentId;
    /** 结果三态：SUCCESS / FAIL / TIMEOUT */
    private PatrolStatus status;
    /** 轻量质量分（0-1；仅成功样本且可评估时有值） */
    private Double score;
    /** 拨测耗时（毫秒；TIMEOUT 时记录为超时预算值） */
    private Long durationMs;
    /** 错误摘要（FAIL/TIMEOUT 时的原因，截断存储） */
    private String errorSummary;
    /** 在线回放产生的关联 traceId（可关联查询链路详情） */
    private String traceId;
    private String createTime;
}
