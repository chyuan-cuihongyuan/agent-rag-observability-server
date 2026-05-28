package cn.chyuan.ai.observability.infrastructure.dao.repository;

import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.AgentDecisionLogMapper;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.ChatResultLogMapper;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.RagRetrievalLogMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.AgentDecisionLogPO;
import cn.chyuan.ai.observability.infrastructure.dao.po.ChatResultLogPO;
import cn.chyuan.ai.observability.infrastructure.dao.po.RagRetrievalLogPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class MysqlLogRepository {

    @Resource
    private AgentDecisionLogMapper agentDecisionLogMapper;

    @Resource
    private RagRetrievalLogMapper ragRetrievalLogMapper;

    @Resource
    private ChatResultLogMapper chatResultLogMapper;

    public void saveDecisionLog(AgentDecisionEntity entity) {
        try {
            agentDecisionLogMapper.insert(AgentDecisionLogPO.builder()
                    .traceId(entity.getTraceId()).sourceService(entity.getSourceService())
                    .tenantId(entity.getTenantId()).ownerUserId(entity.getOwnerUserId())
                    .sessionId(entity.getSessionId()).agentId(entity.getAgentId())
                    .userQuery(entity.getUserQuery()).intentType(entity.getIntentType())
                    .selectedToolList(entity.getSelectedToolList()).decisionReason(entity.getDecisionReason())
                    .branchType(entity.getBranchType()).planSteps(entity.getPlanSteps())
                    .toolCallTimes(entity.getToolCallTimes()).toolRetryTimes(entity.getToolRetryTimes())
                    .agentStatus(entity.getAgentStatus()).costTimeMs(entity.getCostTimeMs())
                    .modelVersion(entity.getModelVersion()).errorMessage(entity.getErrorMessage())
                    .createTime(entity.getCreateTime()).build());
        } catch (Exception e) {
            log.debug("MySQL save decision log error: {}", e.getMessage());
        }
    }

    public void saveRetrievalLog(RagRetrievalEntity entity) {
        try {
            ragRetrievalLogMapper.insert(RagRetrievalLogPO.builder()
                    .traceId(entity.getTraceId()).sourceService(entity.getSourceService())
                    .tenantId(entity.getTenantId()).ownerUserId(entity.getOwnerUserId())
                    .sessionId(entity.getSessionId()).agentId(entity.getAgentId())
                    .queryText(entity.getQueryText()).rewriteText(entity.getRewriteText())
                    .retrievalTopk(entity.getRetrievalTopk()).retrievalCount(entity.getRetrievalCount())
                    .sourceDocs(entity.getSourceDocs()).rerankScores(entity.getRerankScores())
                    .emptyRetrieval(entity.getEmptyRetrieval()).retrievalCostMs(entity.getRetrievalCostMs())
                    .retrievalStages(entity.getRetrievalStages()).ragStrategyVersion(entity.getRagStrategyVersion())
                    .createTime(entity.getCreateTime()).build());
        } catch (Exception e) {
            log.debug("MySQL save retrieval log error: {}", e.getMessage());
        }
    }

    public void saveChatResultLog(ChatResultEntity entity) {
        try {
            chatResultLogMapper.insert(ChatResultLogPO.builder()
                    .traceId(entity.getTraceId()).sourceService(entity.getSourceService())
                    .tenantId(entity.getTenantId()).ownerUserId(entity.getOwnerUserId())
                    .sessionId(entity.getSessionId()).agentId(entity.getAgentId())
                    .question(entity.getQuestion()).answer(entity.getAnswer())
                    .promptTokens(entity.getPromptTokens()).completionTokens(entity.getCompletionTokens())
                    .totalCostTimeMs(entity.getTotalCostTimeMs()).finalStatus(entity.getFinalStatus())
                    .modelVersion(entity.getModelVersion()).createTime(entity.getCreateTime()).build());
        } catch (Exception e) {
            log.debug("MySQL save chat result log error: {}", e.getMessage());
        }
    }
}
