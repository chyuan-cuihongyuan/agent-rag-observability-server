package cn.chyuan.ai.observability.domain.observe.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentDecisionEntity implements Serializable {
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
    /** OTel GenAI 语义约定附加属性（服务端派生，ES 文档附加字段） */
    private Map<String, String> otelAttributes;
}
