package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IJudgeCacheRepository;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * judge 缓存管理控制器（工单 0176 X7）—
 * POST /api/v1/eval/judge-cache/clear?rubricId=（按 rubric 失效清除）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval/judge-cache")
public class JudgeCacheController {

    private final IJudgeCacheRepository judgeCacheRepository;

    public JudgeCacheController(IJudgeCacheRepository judgeCacheRepository) {
        this.judgeCacheRepository = judgeCacheRepository;
    }

    @PostMapping("/clear")
    public Response<Map<String, Object>> clear(@RequestParam String rubricId) {
        if (rubricId == null || rubricId.isBlank()) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "rubricId 不能为空");
        }
        int cleared = judgeCacheRepository.clearByRubric(rubricId.trim());
        return Response.success(Map.of("cleared", cleared));
    }
}
