package cn.chyuan.ai.observability.api.dto.collect;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentDecisionDTO {
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
    private String createTime;
}
