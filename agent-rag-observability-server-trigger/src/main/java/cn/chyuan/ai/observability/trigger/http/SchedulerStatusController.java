package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.scheduler.SchedulerRunRegistry;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 调度健康控制器（工单 0189 Z6）—
 * GET /api/v1/schedulers/status：全部定时任务（巡检/挖掘/保留清理/漂移检测）最近运行状态。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/schedulers")
public class SchedulerStatusController {

    /** 预期任务清单（注册表之外的永远显示 NEVER，便于发现「该跑没跑」） */
    private static final List<String> EXPECTED = List.of("patrol", "mining", "retention", "drift");

    private final SchedulerRunRegistry schedulerRunRegistry;

    public SchedulerStatusController(SchedulerRunRegistry schedulerRunRegistry) {
        this.schedulerRunRegistry = schedulerRunRegistry;
    }

    @GetMapping("/status")
    public Response<List<Map<String, Object>>> status() {
        return Response.success(schedulerRunRegistry.snapshot(EXPECTED));
    }
}
