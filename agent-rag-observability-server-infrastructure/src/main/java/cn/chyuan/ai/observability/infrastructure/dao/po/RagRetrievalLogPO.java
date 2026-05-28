package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagRetrievalLogPO implements Serializable {
    private Long id;
    private String traceId;
    private String sourceService;
    private String tenantId;
    private String ownerUserId;
    private String sessionId;
    private String agentId;
    private String queryText;
    private String rewriteText;
    private Integer retrievalTopk;
    private Integer retrievalCount;
    private String sourceDocs;
    private String rerankScores;
    private Integer emptyRetrieval;
    private Integer retrievalCostMs;
    private String retrievalStages;
    private String ragStrategyVersion;
    private String createTime;
}
