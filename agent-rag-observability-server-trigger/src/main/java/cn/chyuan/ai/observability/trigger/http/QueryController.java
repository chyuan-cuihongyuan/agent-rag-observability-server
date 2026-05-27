package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.query.FullTraceDTO;
import cn.chyuan.ai.observability.api.dto.query.TraceQueryDTO;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.domain.observe.service.ObserveQueryService;
import cn.chyuan.ai.observability.types.response.Response;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/query")
public class QueryController {

    private final ObserveQueryService observeQueryService;

    public QueryController(ObserveQueryService observeQueryService) {
        this.observeQueryService = observeQueryService;
    }

    @GetMapping("/trace/{traceId}")
    public Response<FullTraceDTO> queryTrace(@PathVariable String traceId) {
        AgentDecisionEntity decision = observeQueryService.queryDecisionByTraceId(traceId);
        RagRetrievalEntity retrieval = observeQueryService.queryRetrievalByTraceId(traceId);
        ChatResultEntity chatResult = observeQueryService.queryChatResultByTraceId(traceId);

        FullTraceDTO dto = FullTraceDTO.builder()
                .traceId(traceId)
                .agentDecision(decision != null ? JSON.parseObject(JSON.toJSONString(decision)) : null)
                .ragRetrieval(retrieval != null ? JSON.parseObject(JSON.toJSONString(retrieval)) : null)
                .chatResult(chatResult != null ? JSON.parseObject(JSON.toJSONString(chatResult)) : null)
                .sessionId(decision != null ? decision.getSessionId() : (retrieval != null ? retrieval.getSessionId() : ""))
                .ownerUserId(decision != null ? decision.getOwnerUserId() : "")
                .sourceService(decision != null ? decision.getSourceService() : "")
                .createTime(decision != null ? decision.getCreateTime() : "")
                .build();
        return Response.success(dto);
    }

    @PostMapping("/trace/list")
    public Response<Map<String, Object>> queryTraceList(@RequestBody TraceQueryDTO queryDTO) {
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

        int page = queryDTO.getPage() != null ? queryDTO.getPage() : 1;
        int size = queryDTO.getSize() != null ? queryDTO.getSize() : 20;

        return Response.success(observeQueryService.queryTraceList(condition, page, size));
    }

    @GetMapping("/session/{sessionId}")
    public Response<List<AgentDecisionEntity>> queryBySession(@PathVariable String sessionId,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return Response.success(observeQueryService.queryBySessionId(sessionId, page, size));
    }

    @GetMapping("/user/{userId}/traces")
    public Response<List<AgentDecisionEntity>> queryByUser(@PathVariable String userId,
                                                           @RequestParam String tenantId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return Response.success(observeQueryService.queryByUserId(tenantId, userId, page, size));
    }
}
