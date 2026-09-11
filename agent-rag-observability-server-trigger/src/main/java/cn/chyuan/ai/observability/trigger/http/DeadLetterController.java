package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.dlq.model.entity.DeadLetterEntity;
import cn.chyuan.ai.observability.domain.dlq.service.DeadLetterService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 死信管理控制器（工单 0180 Y4）—
 * GET /api/v1/dlq/list?status=&page=&size=、GET /api/v1/dlq/stats、
 * POST /api/v1/dlq/{id}/replay（重放单条）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/dlq")
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    public DeadLetterController(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    @GetMapping("/list")
    public Response<List<DeadLetterEntity>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return Response.success(deadLetterService.list(status, page, size));
    }

    @GetMapping("/stats")
    public Response<Map<String, Object>> stats() {
        return Response.success(deadLetterService.stats());
    }

    @PostMapping("/{id}/replay")
    public Response<Map<String, Object>> replay(@PathVariable long id) {
        try {
            return Response.success(deadLetterService.replay(id));
        } catch (Exception e) {
            log.error("死信重放异常", e);
            return Response.fail(ResponseCode.ERROR, "重放失败: " + e.getMessage());
        }
    }
}
