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
public class RagRetrievalEntity implements Serializable {
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
    /** OTel GenAI 语义约定附加属性（服务端派生，ES 文档附加字段） */
    private Map<String, String> otelAttributes;
}
