package cn.chyuan.ai.observability.api.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FullTraceDTO {
    private String traceId;
    private String sessionId;
    private String ownerUserId;
    private String agentId;
    private String sourceService;
    private String createTime;
    private Map<String, Object> agentDecision;
    private Map<String, Object> ragRetrieval;
    private Map<String, Object> chatResult;
    /** 工具调用列表 */
    private List<Map<String, Object>> toolCalls;
    /** 记忆检索结果列表（支持同一 Trace 多次记忆召回） */
    private List<Map<String, Object>> memoryRecalls;
}
