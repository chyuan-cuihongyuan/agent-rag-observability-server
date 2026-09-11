package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.insight.model.entity.DriftEventEntity;
import cn.chyuan.ai.observability.domain.insight.adapter.repository.IDriftEventRepository;
import cn.chyuan.ai.observability.domain.insight.service.DriftDetectionService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 漂移检测控制器（工单 0154 U8）—
 * POST /api/v1/insight/drift/run（手动执行一轮对比）、GET /api/v1/insight/drift/events（事件历史）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/insight/drift")
public class DriftController {

    private final DriftDetectionService driftDetectionService;
    private final IDriftEventRepository driftEventRepository;

    public DriftController(DriftDetectionService driftDetectionService,
                           IDriftEventRepository driftEventRepository) {
        this.driftDetectionService = driftDetectionService;
        this.driftEventRepository = driftEventRepository;
    }

    @PostMapping("/run")
    public Response<DriftEventEntity> run() {
        try {
            DriftEventEntity event = driftDetectionService.runOnce();
            if (event == null) {
                return Response.success(null);
            }
            return Response.success(event);
        } catch (Exception e) {
            log.error("手动漂移检测失败", e);
            return Response.fail(ResponseCode.ERROR, "漂移检测失败: " + e.getMessage());
        }
    }

    @GetMapping("/events")
    public Response<List<DriftEventEntity>> events(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);
        return Response.success(driftEventRepository.queryList(p, s));
    }
}
