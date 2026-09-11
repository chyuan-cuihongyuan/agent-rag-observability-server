package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.observe.model.valobj.SessionRollup;
import cn.chyuan.ai.observability.domain.observe.model.valobj.SessionSummary;
import cn.chyuan.ai.observability.domain.observe.service.SessionRollupService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import cn.chyuan.ai.observability.trigger.http.support.RequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话聚合视图控制器（工单 0147 U1，借鉴 Langfuse Sessions）—
 * 多轮 trace 拼一次会话：GET /api/v1/sessions/{sessionId}/rollup（单会话聚合）、
 * GET /api/v1/sessions/rollups?limit=（近期会话列表，按末次活动降序）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/sessions")
public class SessionInsightController {

    private final SessionRollupService sessionRollupService;

    public SessionInsightController(SessionRollupService sessionRollupService) {
        this.sessionRollupService = sessionRollupService;
    }

    @GetMapping("/{sessionId}/rollup")
    public Response<SessionRollup> rollup(@PathVariable String sessionId) {
        String validationError = RequestValidator.validateId("sessionId", sessionId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        SessionRollup rollup = sessionRollupService.queryRollup(sessionId);
        if (rollup == null) {
            return Response.fail(ResponseCode.ERROR, "会话不存在或无轨迹: " + sessionId);
        }
        return Response.success(rollup);
    }

    @GetMapping("/rollups")
    public Response<List<SessionSummary>> rollups(@RequestParam(defaultValue = "20") int limit) {
        return Response.success(sessionRollupService.queryRecentSessions(limit));
    }
}
