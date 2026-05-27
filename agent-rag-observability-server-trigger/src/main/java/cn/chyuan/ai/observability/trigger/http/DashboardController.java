package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.query.DashboardDTO;
import cn.chyuan.ai.observability.domain.observe.service.DashboardService;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public Response<DashboardDTO.Overview> overview(@RequestParam(defaultValue = "1") int days) {
        String endTime = LocalDateTime.now().format(FMT);
        String startTime = LocalDateTime.now().minusDays(days).format(FMT);

        long total = dashboardService.countRequests(startTime, endTime);
        long failCount = dashboardService.countFails(startTime, endTime);
        double successRate = total > 0 ? (double)(total - failCount) / total * 100 : 100.0;
        double avgCost = dashboardService.avgCostTime(startTime, endTime);
        double emptyRate = dashboardService.emptyRetrievalRate(startTime, endTime);

        DashboardDTO.Overview overview = DashboardDTO.Overview.builder()
                .totalRequests(total)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .avgCostTimeMs(Math.round(avgCost * 100.0) / 100.0)
                .emptyRetrievalRate(Math.round(emptyRate * 100.0) / 100.0)
                .failRate(Math.round((double) failCount / Math.max(total, 1) * 100 * 100.0) / 100.0)
                .build();
        return Response.success(overview);
    }

    @GetMapping("/trend")
    public Response<List<Map<String, Object>>> trend(
            @RequestParam(defaultValue = "1") int days,
            @RequestParam(defaultValue = "hour") String interval) {
        String endTime = LocalDateTime.now().format(FMT);
        String startTime = LocalDateTime.now().minusDays(days).format(FMT);
        return Response.success(dashboardService.getTrend(startTime, endTime, interval));
    }

    @GetMapping("/branch_distribution")
    public Response<List<Map<String, Object>>> branchDistribution(@RequestParam(defaultValue = "7") int days) {
        String endTime = LocalDateTime.now().format(FMT);
        String startTime = LocalDateTime.now().minusDays(days).format(FMT);
        return Response.success(dashboardService.getBranchDistribution(startTime, endTime));
    }

    @GetMapping("/tool_usage")
    public Response<List<Map<String, Object>>> toolUsage(@RequestParam(defaultValue = "7") int days) {
        String endTime = LocalDateTime.now().format(FMT);
        String startTime = LocalDateTime.now().minusDays(days).format(FMT);
        return Response.success(dashboardService.getToolUsage(startTime, endTime));
    }

    @GetMapping("/error_ranking")
    public Response<List<Map<String, Object>>> errorRanking(@RequestParam(defaultValue = "7") int days) {
        String endTime = LocalDateTime.now().format(FMT);
        String startTime = LocalDateTime.now().minusDays(days).format(FMT);
        return Response.success(dashboardService.getErrorRanking(startTime, endTime));
    }
}
