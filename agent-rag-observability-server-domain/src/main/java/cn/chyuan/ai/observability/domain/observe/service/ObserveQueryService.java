package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IAgentDecisionRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ObserveQueryService {

    private final IAgentDecisionRepository agentDecisionRepository;
    private final IRagRetrievalRepository ragRetrievalRepository;
    private final IChatResultRepository chatResultRepository;

    public ObserveQueryService(IAgentDecisionRepository agentDecisionRepository,
                               IRagRetrievalRepository ragRetrievalRepository,
                               IChatResultRepository chatResultRepository) {
        this.agentDecisionRepository = agentDecisionRepository;
        this.ragRetrievalRepository = ragRetrievalRepository;
        this.chatResultRepository = chatResultRepository;
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
