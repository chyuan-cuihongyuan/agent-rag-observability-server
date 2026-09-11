package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.insight.service.LatencyInsightService;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 慢链路洞察控制器（工单 0153 U7）—
 * GET /api/v1/insight/latency?days=（p50/p95/p99 分位数概览）、
 * GET /api/v1/insight/slow?days=&topN=（慢 trace TopN）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/insight")
public class LatencyInsightController {

    private final LatencyInsightService latencyInsightService;

    public LatencyInsightController(LatencyInsightService latencyInsightService) {
        this.latencyInsightService = latencyInsightService;
    }

    @GetMapping("/latency")
    public Response<Map<String, Object>> latency(@RequestParam(defaultValue = "7") int days) {
        return Response.success(latencyInsightService.latencyOverview(days));
    }

    @GetMapping("/slow")
    public Response<List<Map<String, Object>>> slow(@RequestParam(defaultValue = "7") int days,
                                                    @RequestParam(defaultValue = "10") int topN) {
        return Response.success(latencyInsightService.slowTopN(days, topN));
    }
}
