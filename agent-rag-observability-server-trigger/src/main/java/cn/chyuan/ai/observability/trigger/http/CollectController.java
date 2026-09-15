package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.collect.*;
import cn.chyuan.ai.observability.domain.observe.model.entity.*;
import cn.chyuan.ai.observability.domain.observe.service.ObserveCollectService;
import cn.chyuan.ai.observability.types.response.Response;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin(origins = {"${cors.allowed-origins:http://localhost:3001,http://localhost:3000}"})  // loop-651：外置（对齐 mcp 惯例），生产经 nginx 同源代理不受影响
@RequestMapping("/api/v1/collect")
public class CollectController {

    private final ObserveCollectService observeCollectService;

    public CollectController(ObserveCollectService observeCollectService) {
        this.observeCollectService = observeCollectService;
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
        return Response.success("ok");
    }

    @PostMapping("/tool_call")
    public Response<String> collectToolCall(@RequestBody ToolCallLogDTO dto) {
        ToolCallLogEntity entity = ToolCallLogEntity.builder()
                .traceId(dto.getTraceId()).spanId(dto.getSpanId())
                .parentSpanId(dto.getParentSpanId()).toolName(dto.getToolName())
                .toolInput(dto.getToolInput()).toolOutput(dto.getToolOutput())
                .status(dto.getStatus()).costTimeMs(dto.getCostTimeMs())
                .errorMessage(dto.getErrorMessage()).callOrder(dto.getCallOrder())
                .createTime(dto.getCreateTime()).build();
        observeCollectService.collectToolCallLog(entity);
        return Response.success("ok");
    }

    @PostMapping("/memory_recall")
    public Response<String> collectMemoryRecall(@RequestBody MemoryRecallLogDTO dto) {
        MemoryRecallLogEntity entity = MemoryRecallLogEntity.builder()
                .traceId(dto.getTraceId()).queryText(dto.getQueryText())
                .sessionMemoryCount(dto.getSessionMemoryCount())
                .agentMemoryCount(dto.getAgentMemoryCount())
                .sessionMemoryScores(parseDoubleList(dto.getSessionMemoryScores()))
                .agentMemoryScores(parseDoubleList(dto.getAgentMemoryScores()))
                .injectContent(dto.getInjectContent()).costTimeMs(dto.getCostTimeMs())
                .createTime(dto.getCreateTime()).build();
        observeCollectService.collectMemoryRecallLog(entity);
        return Response.success("ok");
    }

    @PostMapping("/batch")
    public Response<String> collectBatch(@RequestBody ObserveBatchDTO dto) {
        // 校验同一 batch 内所有子 DTO 的 traceId 一致性
        String batchTraceId = dto.getAgentDecision() != null ? dto.getAgentDecision().getTraceId() : null;
        if (batchTraceId == null && dto.getRagRetrieval() != null) {
            batchTraceId = dto.getRagRetrieval().getTraceId();
        }
        if (batchTraceId == null && dto.getChatResult() != null) {
            batchTraceId = dto.getChatResult().getTraceId();
        }
        if (batchTraceId != null) {
            String finalBatchTraceId = batchTraceId;
            if (dto.getRagRetrieval() != null && !finalBatchTraceId.equals(dto.getRagRetrieval().getTraceId())) {
                return Response.fail(cn.chyuan.ai.observability.types.response.ResponseCode.ILLEGAL_PARAMETER,
                        "batch 请求中各子 DTO 的 traceId 不一致");
            }
            if (dto.getChatResult() != null && !finalBatchTraceId.equals(dto.getChatResult().getTraceId())) {
                return Response.fail(cn.chyuan.ai.observability.types.response.ResponseCode.ILLEGAL_PARAMETER,
                        "batch 请求中各子 DTO 的 traceId 不一致");
            }
            if (dto.getToolCalls() != null) {
                for (ToolCallLogDTO t : dto.getToolCalls()) {
                    if (!finalBatchTraceId.equals(t.getTraceId())) {
                        return Response.fail(cn.chyuan.ai.observability.types.response.ResponseCode.ILLEGAL_PARAMETER,
                                "batch 请求中各子 DTO 的 traceId 不一致");
                    }
                }
            }
            if (dto.getMemoryRecalls() != null) {
                for (MemoryRecallLogDTO m : dto.getMemoryRecalls()) {
                    if (!finalBatchTraceId.equals(m.getTraceId())) {
                        return Response.fail(cn.chyuan.ai.observability.types.response.ResponseCode.ILLEGAL_PARAMETER,
                                "batch 请求中各子 DTO 的 traceId 不一致");
                    }
                }
            }
        }

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
        }
        if (dto.getToolCalls() != null) {
            for (ToolCallLogDTO t : dto.getToolCalls()) {
                observeCollectService.collectToolCallLog(ToolCallLogEntity.builder()
                        .traceId(t.getTraceId()).spanId(t.getSpanId())
                        .parentSpanId(t.getParentSpanId()).toolName(t.getToolName())
                        .toolInput(t.getToolInput()).toolOutput(t.getToolOutput())
                        .status(t.getStatus()).costTimeMs(t.getCostTimeMs())
                        .errorMessage(t.getErrorMessage()).callOrder(t.getCallOrder())
                        .createTime(t.getCreateTime()).build());
            }
        }
        if (dto.getMemoryRecalls() != null) {
            for (MemoryRecallLogDTO m : dto.getMemoryRecalls()) {
                observeCollectService.collectMemoryRecallLog(MemoryRecallLogEntity.builder()
                        .traceId(m.getTraceId()).queryText(m.getQueryText())
                        .sessionMemoryCount(m.getSessionMemoryCount())
                        .agentMemoryCount(m.getAgentMemoryCount())
                        .sessionMemoryScores(parseDoubleList(m.getSessionMemoryScores()))
                        .agentMemoryScores(parseDoubleList(m.getAgentMemoryScores()))
                        .injectContent(m.getInjectContent()).costTimeMs(m.getCostTimeMs())
                        .createTime(m.getCreateTime()).build());
            }
        }
        return Response.success("ok");
    }

    /**
     * 将 JSON 字符串格式的分数列表解析为 List<Double>
     * DTO 外部传入 String（如 "[0.9,0.8]"），Entity 内部使用 List<Double>
     */
    @SuppressWarnings("unchecked")
    private static java.util.List<Double> parseDoubleList(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.parseArray(json, Double.class);
        } catch (Exception e) {
            return null;
        }
    }
}
