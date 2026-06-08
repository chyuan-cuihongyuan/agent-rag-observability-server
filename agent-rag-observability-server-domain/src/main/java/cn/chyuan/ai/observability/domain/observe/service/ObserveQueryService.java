package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.*;
import cn.chyuan.ai.observability.domain.observe.model.entity.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObserveQueryService {

    private final IAgentDecisionRepository agentDecisionRepository;
    private final IRagRetrievalRepository ragRetrievalRepository;
    private final IChatResultRepository chatResultRepository;
    private final IToolCallLogRepository toolCallLogRepository;
    private final IMemoryRecallLogRepository memoryRecallLogRepository;

    public ObserveQueryService(IAgentDecisionRepository agentDecisionRepository,
                               IRagRetrievalRepository ragRetrievalRepository,
                               IChatResultRepository chatResultRepository,
                               IToolCallLogRepository toolCallLogRepository,
                               IMemoryRecallLogRepository memoryRecallLogRepository) {
        this.agentDecisionRepository = agentDecisionRepository;
        this.ragRetrievalRepository = ragRetrievalRepository;
        this.chatResultRepository = chatResultRepository;
        this.toolCallLogRepository = toolCallLogRepository;
        this.memoryRecallLogRepository = memoryRecallLogRepository;
    }

    public AgentDecisionEntity queryDecisionByTraceId(String traceId) {
        return agentDecisionRepository.queryByTraceId(traceId);
    }

    public RagRetrievalEntity queryRetrievalByTraceId(String traceId) {
        return ragRetrievalRepository.queryByTraceId(traceId);
    }

    public ChatResultEntity queryChatResultByTraceId(String traceId) {
        return chatResultRepository.queryByTraceId(traceId);
    }

    public List<ToolCallLogEntity> queryToolCallsByTraceId(String traceId) {
        return toolCallLogRepository.queryByTraceId(traceId);
    }

    public List<MemoryRecallLogEntity> queryMemoryRecallsByTraceId(String traceId) {
        return memoryRecallLogRepository.queryByTraceId(traceId);
    }

    public Map<String, Object> queryTraceList(Map<String, Object> condition, int page, int size) {
        List<AgentDecisionEntity> list = agentDecisionRepository.queryByCondition(condition, page, size);
        long total = agentDecisionRepository.countByCondition(condition);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public List<AgentDecisionEntity> queryBySessionId(String sessionId, int page, int size) {
        return agentDecisionRepository.queryBySessionId(sessionId, page, size);
    }

    public List<AgentDecisionEntity> queryByUserId(String tenantId, String ownerUserId, int page, int size) {
        return agentDecisionRepository.queryByUserId(tenantId, ownerUserId, page, size);
    }
}
