package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.cache.ICachePort;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.*;
import cn.chyuan.ai.observability.domain.observe.model.entity.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ObserveCollectService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IAgentDecisionRepository agentDecisionRepository;
    private final IRagRetrievalRepository ragRetrievalRepository;
    private final IChatResultRepository chatResultRepository;
    private final IToolCallLogRepository toolCallLogRepository;
    private final IMemoryRecallLogRepository memoryRecallLogRepository;
    private final ICachePort cachePort;

    public ObserveCollectService(IAgentDecisionRepository agentDecisionRepository,
                                  IRagRetrievalRepository ragRetrievalRepository,
                                  IChatResultRepository chatResultRepository,
                                  IToolCallLogRepository toolCallLogRepository,
                                  IMemoryRecallLogRepository memoryRecallLogRepository,
                                  ICachePort cachePort) {
        this.agentDecisionRepository = agentDecisionRepository;
        this.ragRetrievalRepository = ragRetrievalRepository;
        this.chatResultRepository = chatResultRepository;
        this.toolCallLogRepository = toolCallLogRepository;
        this.memoryRecallLogRepository = memoryRecallLogRepository;
        this.cachePort = cachePort;
    }

    public void collectAgentDecision(AgentDecisionEntity entity) {
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now().format(FMT));
        }
        agentDecisionRepository.save(entity);
        cachePort.increment("agent_decision");
    }

    public void collectRagRetrieval(RagRetrievalEntity entity) {
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now().format(FMT));
        }
        ragRetrievalRepository.save(entity);
        cachePort.increment("rag_retrieval");
    }

    public void collectChatResult(ChatResultEntity entity) {
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now().format(FMT));
        }
        chatResultRepository.save(entity);
        cachePort.increment("chat_result");
    }

    public void collectToolCallLog(ToolCallLogEntity entity) {
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now().format(FMT));
        }
        toolCallLogRepository.save(entity);
        cachePort.increment("tool_call_log");
    }

    public void collectMemoryRecallLog(MemoryRecallLogEntity entity) {
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now().format(FMT));
        }
        memoryRecallLogRepository.save(entity);
        cachePort.increment("memory_recall_log");
    }
}
