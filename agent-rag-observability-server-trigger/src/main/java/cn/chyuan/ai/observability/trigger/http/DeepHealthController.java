package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.health.HealthAggregator;
import cn.chyuan.ai.observability.domain.health.HealthProbe;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 深度健康自检控制器（工单 0181 Y5）—
 * GET /api/v1/health/deep：逐组件探测（DB/ES/MQ…），聚合 component/status/latencyMs/error
 * 与整体 UP/DEGRADED/DOWN（DB 为关键组件：DB DOWN 即整体 DOWN）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/health")
public class DeepHealthController {

    private final List<HealthProbe> probes;

    public DeepHealthController(List<HealthProbe> probes) {
        this.probes = probes;
    }

    @GetMapping("/deep")
    public Response<Map<String, Object>> deep() {
        List<HealthProbe.ProbeResult> results = new ArrayList<>();
        List<Boolean> criticals = new ArrayList<>();
        for (HealthProbe probe : probes) {
            try {
                results.add(probe.probe());
            } catch (Exception e) {
                results.add(HealthProbe.ProbeResult.fail(probe.name(), 0, e.getMessage()));
            }
            criticals.add(probe.critical());
        }
        String overall = HealthAggregator.overall(results, criticals);
        List<Map<String, Object>> components = results.stream().map(HealthAggregator::toMap).toList();
        return Response.success(Map.of("overall", overall, "components", components));
    }
}
