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

    /**
     * 游标分页（SELFLOOP3 loop-344，工单 0486/0487）：search_after 深分页（from+size 超 10000 即败）。
     * cursor = 上一页最后一条 createTime|traceId；结果不足 size 时 nextCursor=null（终页）。
     */
    public Map<String, Object> queryTraceListAfter(Map<String, Object> condition, int size, String cursor) {
        String afterCreateTime = null;
        String afterTraceId = null;
        if (cursor != null && cursor.contains("|")) {
            String[] parts = cursor.split("\\|", 2);
            afterCreateTime = parts[0];
            afterTraceId = parts[1];
        }
        List<AgentDecisionEntity> list = agentDecisionRepository.queryByConditionAfter(condition, size, afterCreateTime, afterTraceId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("size", size);
        if (list.size() >= size) {
            AgentDecisionEntity last = list.get(list.size() - 1);
            result.put("nextCursor", last.getCreateTime() + "|" + last.getTraceId());
        } else {
            result.put("nextCursor", null);
        }
        return result;
    }

    public List<AgentDecisionEntity> queryBySessionId(String sessionId, int page, int size) {
        return agentDecisionRepository.queryBySessionId(sessionId, page, size);
    }

    public List<AgentDecisionEntity> queryByUserId(String tenantId, String ownerUserId, int page, int size) {
        return agentDecisionRepository.queryByUserId(tenantId, ownerUserId, page, size);
    }
}
