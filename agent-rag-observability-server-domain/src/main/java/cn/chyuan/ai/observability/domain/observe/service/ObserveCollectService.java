package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IAgentDecisionRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ObserveCollectService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IAgentDecisionRepository agentDecisionRepository;
    private final IRagRetrievalRepository ragRetrievalRepository;
    private final IChatResultRepository chatResultRepository;

    public ObserveCollectService(IAgentDecisionRepository agentDecisionRepository,
                                  IRagRetrievalRepository ragRetrievalRepository,
                                  IChatResultRepository chatResultRepository) {
        this.agentDecisionRepository = agentDecisionRepository;
        this.ragRetrievalRepository = ragRetrievalRepository;
        this.chatResultRepository = chatResultRepository;
    }

    public void collectAgentDecision(AgentDecisionEntity entity) {
        try {
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(LocalDateTime.now().format(FMT));
            }
            agentDecisionRepository.save(entity);
        } catch (Exception e) {
            // domain layer swallows — trigger layer logs
        }
    }

    public void collectRagRetrieval(RagRetrievalEntity entity) {
        try {
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(LocalDateTime.now().format(FMT));
            }
            ragRetrievalRepository.save(entity);
        } catch (Exception e) {
            // domain layer swallows — trigger layer logs
        }
    }

    public void collectChatResult(ChatResultEntity entity) {
        try {
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(LocalDateTime.now().format(FMT));
            }
            chatResultRepository.save(entity);
        } catch (Exception e) {
            // domain layer swallows — trigger layer logs
        }
    }
}
