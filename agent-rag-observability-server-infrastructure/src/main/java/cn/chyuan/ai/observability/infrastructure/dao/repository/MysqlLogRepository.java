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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Repository
public class MysqlLogRepository {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AgentDecisionLogMapper agentDecisionLogMapper;

    @Resource
    private RagRetrievalLogMapper ragRetrievalLogMapper;

    @Resource
    private ChatResultLogMapper chatResultLogMapper;

    public void saveDecisionLog(AgentDecisionEntity entity) {
        try {
            agentDecisionLogMapper.insert(AgentDecisionLogPO.builder()
                    .traceId(text(entity.getTraceId())).sourceService(text(entity.getSourceService()))
                    .tenantId(text(entity.getTenantId())).ownerUserId(text(entity.getOwnerUserId()))
                    .sessionId(text(entity.getSessionId())).agentId(text(entity.getAgentId()))
                    .userQuery(text(entity.getUserQuery())).intentType(text(entity.getIntentType()))
                    .selectedToolList(entity.getSelectedToolList()).decisionReason(entity.getDecisionReason())
                    .branchType(text(entity.getBranchType())).planSteps(entity.getPlanSteps())
                    .toolCallTimes(number(entity.getToolCallTimes())).toolRetryTimes(number(entity.getToolRetryTimes()))
                    .agentStatus(text(entity.getAgentStatus())).costTimeMs(number(entity.getCostTimeMs()))
                    .modelVersion(text(entity.getModelVersion())).errorMessage(entity.getErrorMessage())
                    .createTime(createTime(entity.getCreateTime())).build());
        } catch (Exception e) {
            log.debug("MySQL save decision log error: {}", e.getMessage());
        }
    }

    public void saveRetrievalLog(RagRetrievalEntity entity) {
        try {
            ragRetrievalLogMapper.insert(RagRetrievalLogPO.builder()
                    .traceId(text(entity.getTraceId())).sourceService(text(entity.getSourceService()))
                    .tenantId(text(entity.getTenantId())).ownerUserId(text(entity.getOwnerUserId()))
                    .sessionId(text(entity.getSessionId())).agentId(text(entity.getAgentId()))
                    .queryText(text(entity.getQueryText())).rewriteText(entity.getRewriteText())
                    .retrievalTopk(number(entity.getRetrievalTopk())).retrievalCount(number(entity.getRetrievalCount()))
                    .sourceDocs(entity.getSourceDocs()).rerankScores(entity.getRerankScores())
                    .emptyRetrieval(number(entity.getEmptyRetrieval())).retrievalCostMs(entity.getRetrievalCostMs())
                    .retrievalStages(entity.getRetrievalStages()).ragStrategyVersion(text(entity.getRagStrategyVersion()))
                    .createTime(createTime(entity.getCreateTime())).build());
        } catch (Exception e) {
            log.debug("MySQL save retrieval log error: {}", e.getMessage());
        }
    }

    public void saveChatResultLog(ChatResultEntity entity) {
        try {
            chatResultLogMapper.insert(ChatResultLogPO.builder()
                    .traceId(text(entity.getTraceId())).sourceService(text(entity.getSourceService()))
                    .tenantId(text(entity.getTenantId())).ownerUserId(text(entity.getOwnerUserId()))
                    .sessionId(text(entity.getSessionId())).agentId(text(entity.getAgentId()))
                    .question(text(entity.getQuestion())).answer(text(entity.getAnswer()))
                    .promptTokens(number(entity.getPromptTokens())).completionTokens(number(entity.getCompletionTokens()))
                    .totalCostTimeMs(number(entity.getTotalCostTimeMs())).finalStatus(text(entity.getFinalStatus()))
                    .modelVersion(text(entity.getModelVersion())).createTime(createTime(entity.getCreateTime())).build());
        } catch (Exception e) {
            log.debug("MySQL save chat result log error: {}", e.getMessage());
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static Integer number(Integer value) {
        return value == null ? 0 : value;
    }

    private static String createTime(String value) {
        return value == null || value.isBlank() ? LocalDateTime.now().format(DATE_TIME_FORMATTER) : value;
    }
}
