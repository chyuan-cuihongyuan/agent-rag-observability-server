package cn.chyuan.ai.observability.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagRetrievalReport {
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
    /** 客户端事件时间，格式 yyyy-MM-dd HH:mm:ss；为空时服务端按接收时间补 */
    private String createTime;
}
