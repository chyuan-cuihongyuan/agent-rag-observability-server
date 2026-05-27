package cn.chyuan.ai.observability.domain.observe.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatResultEntity implements Serializable {
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
    private String createTime;
}
