package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.cache.ICachePort;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IAgentDecisionRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ObserveCollectService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IAgentDecisionRepository agentDecisionRepository;
    private final IRagRetrievalRepository ragRetrievalRepository;
    private final IChatResultRepository chatResultRepository;
    private final ICachePort cachePort;

    public ObserveCollectService(IAgentDecisionRepository agentDecisionRepository,
                                  IRagRetrievalRepository ragRetrievalRepository,
                                  IChatResultRepository chatResultRepository,
                                  ICachePort cachePort) {
        this.agentDecisionRepository = agentDecisionRepository;
        this.ragRetrievalRepository = ragRetrievalRepository;
        this.chatResultRepository = chatResultRepository;
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
}
