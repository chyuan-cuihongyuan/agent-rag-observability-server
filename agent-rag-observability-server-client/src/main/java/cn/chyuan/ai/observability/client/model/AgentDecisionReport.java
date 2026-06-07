package cn.chyuan.ai.observability.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentDecisionReport {
    private String traceId;
    private String sourceService;
    private String tenantId;
    private String ownerUserId;
    private String sessionId;
    private String agentId;
    private String userQuery;
    private String intentType;
    private String selectedToolList;
    private String decisionReason;
    private String branchType;
    private String planSteps;
    private Integer toolCallTimes;
    private Integer toolRetryTimes;
    private String agentStatus;
    private Integer costTimeMs;
    private String modelVersion;
    private String errorMessage;
    /** 客户端事件时间，格式 yyyy-MM-dd HH:mm:ss；为空时服务端按接收时间补 */
    private String createTime;
}
