package cn.chyuan.ai.observability.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatResultReport {
    private String traceId;
    private String sourceService;
    private String tenantId;
    private String ownerUserId;
    private String sessionId;
    private String agentId;
    private String question;
    private String answer;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalCostTimeMs;
    private String finalStatus;
    private String modelVersion;
}
