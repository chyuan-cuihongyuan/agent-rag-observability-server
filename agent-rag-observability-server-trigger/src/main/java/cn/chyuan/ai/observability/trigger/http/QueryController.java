package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.query.FullTraceDTO;
import cn.chyuan.ai.observability.api.dto.query.TraceQueryDTO;
import cn.chyuan.ai.observability.domain.observe.model.entity.*;
import cn.chyuan.ai.observability.domain.observe.service.ObserveQueryService;
import cn.chyuan.ai.observability.domain.observe.service.TraceQualityCalculator;
import cn.chyuan.ai.observability.trigger.http.support.RequestValidator;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/query")
public class QueryController {

    private final ObserveQueryService observeQueryService;
    private final TraceQualityCalculator traceQualityCalculator;

    public QueryController(ObserveQueryService observeQueryService, TraceQualityCalculator traceQualityCalculator) {
        this.observeQueryService = observeQueryService;
        this.traceQualityCalculator = traceQualityCalculator;
    }

    @GetMapping("/trace/{traceId}")
    public Response<FullTraceDTO> queryTrace(@PathVariable String traceId) {
        String validationError = RequestValidator.validateId("traceId", traceId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }

        AgentDecisionEntity decision = observeQueryService.queryDecisionByTraceId(traceId);
        RagRetrievalEntity retrieval = observeQueryService.queryRetrievalByTraceId(traceId);
        ChatResultEntity chatResult = observeQueryService.queryChatResultByTraceId(traceId);
        List<ToolCallLogEntity> toolCalls = observeQueryService.queryToolCallsByTraceId(traceId);
        List<MemoryRecallLogEntity> memoryRecalls = observeQueryService.queryMemoryRecallsByTraceId(traceId);

        // 在线质量评分（实时派生，零 LLM 成本）
        TraceQualityCalculator.TraceQuality quality = traceQualityCalculator.compute(retrieval, chatResult);
        Map<String, Object> qualityMap = new HashMap<>();
        qualityMap.put("retrievalQuality", quality.retrievalQuality());
        qualityMap.put("faithfulness", quality.faithfulness());
        qualityMap.put("answerRelevance", quality.answerRelevance());

        FullTraceDTO dto = FullTraceDTO.builder()
                .traceId(traceId)
                .agentDecision(decision != null ? JSON.parseObject(JSON.toJSONString(decision)) : null)
                .ragRetrieval(retrieval != null ? JSON.parseObject(JSON.toJSONString(retrieval)) : null)
                .chatResult(chatResult != null ? JSON.parseObject(JSON.toJSONString(chatResult)) : null)
                .toolCalls(toolCalls != null && !toolCalls.isEmpty() ?
                        toolCalls.stream()
                                .map(tc -> JSON.parseObject(JSON.toJSONString(tc)))
                                .collect(Collectors.toList()) : null)
                .memoryRecalls(memoryRecalls != null && !memoryRecalls.isEmpty() ?
                        memoryRecalls.stream()
                                .map(mr -> JSON.parseObject(JSON.toJSONString(mr)))
                                .collect(Collectors.toList()) : null)
                .quality(qualityMap)
                .sessionId(decision != null ? decision.getSessionId() : (retrieval != null ? retrieval.getSessionId() : ""))
                .ownerUserId(decision != null ? decision.getOwnerUserId() : "")
                .agentId(decision != null ? decision.getAgentId() : (retrieval != null ? retrieval.getAgentId() : (chatResult != null ? chatResult.getAgentId() : "")))
                .sourceService(decision != null ? decision.getSourceService() : "")
                .createTime(decision != null ? decision.getCreateTime() : "")
                .build();
        return Response.success(dto);
    }

    @PostMapping("/trace/list")
    public Response<Map<String, Object>> queryTraceList(@RequestBody TraceQueryDTO queryDTO) {
        if (queryDTO == null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "查询参数不能为空");
        }

        Map<String, Object> condition = new HashMap<>();
        String validationError = validateTraceQuery(queryDTO);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }

        if (queryDTO.getTenantId() != null) condition.put("tenantId", queryDTO.getTenantId());
        if (queryDTO.getOwnerUserId() != null) condition.put("ownerUserId", queryDTO.getOwnerUserId());
        if (queryDTO.getSessionId() != null) condition.put("sessionId", queryDTO.getSessionId());
        if (queryDTO.getAgentId() != null) condition.put("agentId", queryDTO.getAgentId());
        if (queryDTO.getBranchType() != null) condition.put("branchType", queryDTO.getBranchType());
        if (queryDTO.getAgentStatus() != null) condition.put("agentStatus", queryDTO.getAgentStatus());
        if (queryDTO.getSourceService() != null) condition.put("sourceService", queryDTO.getSourceService());
        if (queryDTO.getStartTime() != null) condition.put("startTime", queryDTO.getStartTime());
        if (queryDTO.getEndTime() != null) condition.put("endTime", queryDTO.getEndTime());

        int page = queryDTO.getPage() != null ? queryDTO.getPage() : 1;
        int size = queryDTO.getSize() != null ? queryDTO.getSize() : 20;

        return Response.success(observeQueryService.queryTraceList(condition, page, size));
    }

    /**
     * 游标分页查询 trace 列表（SELFLOOP3 loop-344，工单 0486/0487）：
     * search_after 深分页；cursor=上一页 nextCursor（createTime|traceId），首页不传。
     */
    @PostMapping("/trace/list/cursor")
    public Response<Map<String, Object>> queryTraceListCursor(@RequestBody TraceQueryDTO queryDTO) {
        if (queryDTO == null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "查询参数不能为空");
        }
        String validationError = validateTraceQuery(queryDTO);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        Map<String, Object> condition = new HashMap<>();
        if (queryDTO.getTenantId() != null) condition.put("tenantId", queryDTO.getTenantId());
        if (queryDTO.getOwnerUserId() != null) condition.put("ownerUserId", queryDTO.getOwnerUserId());
        if (queryDTO.getSessionId() != null) condition.put("sessionId", queryDTO.getSessionId());
        if (queryDTO.getAgentId() != null) condition.put("agentId", queryDTO.getAgentId());
        if (queryDTO.getBranchType() != null) condition.put("branchType", queryDTO.getBranchType());
        if (queryDTO.getAgentStatus() != null) condition.put("agentStatus", queryDTO.getAgentStatus());
        if (queryDTO.getSourceService() != null) condition.put("sourceService", queryDTO.getSourceService());
        if (queryDTO.getStartTime() != null) condition.put("startTime", queryDTO.getStartTime());
        if (queryDTO.getEndTime() != null) condition.put("endTime", queryDTO.getEndTime());

        int size = queryDTO.getSize() != null ? Math.min(Math.max(queryDTO.getSize(), 1), 100) : 20;
        return Response.success(observeQueryService.queryTraceListAfter(condition, size, queryDTO.getCursor()));
    }

    @GetMapping("/session/{sessionId}")
    public Response<List<AgentDecisionEntity>> queryBySession(@PathVariable String sessionId,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        String validationError = RequestValidator.validateId("sessionId", sessionId);
        if (validationError == null) {
            validationError = RequestValidator.validatePage(page, size);
        }
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        return Response.success(observeQueryService.queryBySessionId(sessionId, page, size));
    }

    @GetMapping("/user/{userId}/traces")
    public Response<List<AgentDecisionEntity>> queryByUser(@PathVariable String userId,
                                                           @RequestParam String tenantId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        String validationError = RequestValidator.validateId("userId", userId);
        if (validationError == null) {
            validationError = RequestValidator.validateId("tenantId", tenantId);
        }
        if (validationError == null) {
            validationError = RequestValidator.validatePage(page, size);
        }
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        return Response.success(observeQueryService.queryByUserId(tenantId, userId, page, size));
    }

    private String validateTraceQuery(TraceQueryDTO queryDTO) {
        String validationError = RequestValidator.validateOptionalId("tenantId", queryDTO.getTenantId());
        if (validationError == null) validationError = RequestValidator.validateOptionalId("ownerUserId", queryDTO.getOwnerUserId());
        if (validationError == null) validationError = RequestValidator.validateOptionalId("sessionId", queryDTO.getSessionId());
        if (validationError == null) validationError = RequestValidator.validateOptionalId("agentId", queryDTO.getAgentId());
        if (validationError == null) validationError = RequestValidator.validateOptionalId("sourceService", queryDTO.getSourceService());
        if (validationError == null) validationError = RequestValidator.validateTimeRange(queryDTO.getStartTime(), queryDTO.getEndTime());
        int page = queryDTO.getPage() != null ? queryDTO.getPage() : 1;
        int size = queryDTO.getSize() != null ? queryDTO.getSize() : 20;
        if (validationError == null) validationError = RequestValidator.validatePage(page, size);
        return validationError;
    }
}
