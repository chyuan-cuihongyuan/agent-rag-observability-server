package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.collect.AgentDecisionDTO;
import cn.chyuan.ai.observability.api.dto.collect.ChatResultDTO;
import cn.chyuan.ai.observability.api.dto.collect.ObserveBatchDTO;
import cn.chyuan.ai.observability.api.dto.collect.RagRetrievalDTO;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.domain.observe.service.ObserveCollectService;
import cn.chyuan.ai.observability.infrastructure.redis.DashboardCacheService;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/collect")
public class CollectController {

    private final ObserveCollectService observeCollectService;
    private final DashboardCacheService cacheService;

    public CollectController(ObserveCollectService observeCollectService, DashboardCacheService cacheService) {
        this.observeCollectService = observeCollectService;
        this.cacheService = cacheService;
    }

    @PostMapping("/agent_decision")
    public Response<String> collectAgentDecision(@RequestBody AgentDecisionDTO dto) {
        AgentDecisionEntity entity = AgentDecisionEntity.builder()
                .traceId(dto.getTraceId()).sourceService(dto.getSourceService())
                .tenantId(dto.getTenantId()).ownerUserId(dto.getOwnerUserId())
                .sessionId(dto.getSessionId()).agentId(dto.getAgentId())
                .userQuery(dto.getUserQuery()).intentType(dto.getIntentType())
                .selectedToolList(dto.getSelectedToolList()).decisionReason(dto.getDecisionReason())
                .branchType(dto.getBranchType()).planSteps(dto.getPlanSteps())
                .toolCallTimes(dto.getToolCallTimes()).toolRetryTimes(dto.getToolRetryTimes())
                .agentStatus(dto.getAgentStatus()).costTimeMs(dto.getCostTimeMs())
                .modelVersion(dto.getModelVersion()).errorMessage(dto.getErrorMessage())
                .createTime(dto.getCreateTime()).build();
        observeCollectService.collectAgentDecision(entity);
        cacheService.increment("agent_decision");
        return Response.success("ok");
    }

    @PostMapping("/rag_retrieval")
    public Response<String> collectRagRetrieval(@RequestBody RagRetrievalDTO dto) {
        RagRetrievalEntity entity = RagRetrievalEntity.builder()
                .traceId(dto.getTraceId()).sourceService(dto.getSourceService())
                .tenantId(dto.getTenantId()).ownerUserId(dto.getOwnerUserId())
                .sessionId(dto.getSessionId()).agentId(dto.getAgentId())
                .queryText(dto.getQueryText()).rewriteText(dto.getRewriteText())
                .retrievalTopk(dto.getRetrievalTopk()).retrievalCount(dto.getRetrievalCount())
                .sourceDocs(dto.getSourceDocs()).rerankScores(dto.getRerankScores())
                .emptyRetrieval(dto.getEmptyRetrieval()).retrievalCostMs(dto.getRetrievalCostMs())
                .retrievalStages(dto.getRetrievalStages()).ragStrategyVersion(dto.getRagStrategyVersion())
                .createTime(dto.getCreateTime()).build();
        observeCollectService.collectRagRetrieval(entity);
        cacheService.increment("rag_retrieval");
        return Response.success("ok");
    }

    @PostMapping("/chat_result")
    public Response<String> collectChatResult(@RequestBody ChatResultDTO dto) {
        ChatResultEntity entity = ChatResultEntity.builder()
                .traceId(dto.getTraceId()).sourceService(dto.getSourceService())
                .tenantId(dto.getTenantId()).ownerUserId(dto.getOwnerUserId())
                .sessionId(dto.getSessionId()).agentId(dto.getAgentId())
                .question(dto.getQuestion()).answer(dto.getAnswer())
                .promptTokens(dto.getPromptTokens()).completionTokens(dto.getCompletionTokens())
                .totalCostTimeMs(dto.getTotalCostTimeMs()).finalStatus(dto.getFinalStatus())
                .modelVersion(dto.getModelVersion()).createTime(dto.getCreateTime()).build();
        observeCollectService.collectChatResult(entity);
        cacheService.increment("chat_result");
        return Response.success("ok");
    }

    @PostMapping("/batch")
    public Response<String> collectBatch(@RequestBody ObserveBatchDTO dto) {
        if (dto.getAgentDecision() != null) {
            AgentDecisionDTO a = dto.getAgentDecision();
            observeCollectService.collectAgentDecision(AgentDecisionEntity.builder()
                    .traceId(a.getTraceId()).sourceService(a.getSourceService())
                    .tenantId(a.getTenantId()).ownerUserId(a.getOwnerUserId())
                    .sessionId(a.getSessionId()).agentId(a.getAgentId())
                    .userQuery(a.getUserQuery()).intentType(a.getIntentType())
                    .selectedToolList(a.getSelectedToolList()).decisionReason(a.getDecisionReason())
                    .branchType(a.getBranchType()).planSteps(a.getPlanSteps())
                    .toolCallTimes(a.getToolCallTimes()).toolRetryTimes(a.getToolRetryTimes())
                    .agentStatus(a.getAgentStatus()).costTimeMs(a.getCostTimeMs())
                    .modelVersion(a.getModelVersion()).errorMessage(a.getErrorMessage())
                    .createTime(a.getCreateTime()).build());
            cacheService.increment("agent_decision");
        }
        if (dto.getRagRetrieval() != null) {
            RagRetrievalDTO r = dto.getRagRetrieval();
            observeCollectService.collectRagRetrieval(RagRetrievalEntity.builder()
                    .traceId(r.getTraceId()).sourceService(r.getSourceService())
                    .tenantId(r.getTenantId()).ownerUserId(r.getOwnerUserId())
                    .sessionId(r.getSessionId()).agentId(r.getAgentId())
                    .queryText(r.getQueryText()).rewriteText(r.getRewriteText())
                    .retrievalTopk(r.getRetrievalTopk()).retrievalCount(r.getRetrievalCount())
                    .sourceDocs(r.getSourceDocs()).rerankScores(r.getRerankScores())
                    .emptyRetrieval(r.getEmptyRetrieval()).retrievalCostMs(r.getRetrievalCostMs())
                    .retrievalStages(r.getRetrievalStages()).ragStrategyVersion(r.getRagStrategyVersion())
                    .createTime(r.getCreateTime()).build());
            cacheService.increment("rag_retrieval");
        }
        if (dto.getChatResult() != null) {
            ChatResultDTO c = dto.getChatResult();
            observeCollectService.collectChatResult(ChatResultEntity.builder()
                    .traceId(c.getTraceId()).sourceService(c.getSourceService())
                    .tenantId(c.getTenantId()).ownerUserId(c.getOwnerUserId())
                    .sessionId(c.getSessionId()).agentId(c.getAgentId())
                    .question(c.getQuestion()).answer(c.getAnswer())
                    .promptTokens(c.getPromptTokens()).completionTokens(c.getCompletionTokens())
                    .totalCostTimeMs(c.getTotalCostTimeMs()).finalStatus(c.getFinalStatus())
                    .modelVersion(c.getModelVersion()).createTime(c.getCreateTime()).build());
            cacheService.increment("chat_result");
        }
        return Response.success("ok");
    }
}
