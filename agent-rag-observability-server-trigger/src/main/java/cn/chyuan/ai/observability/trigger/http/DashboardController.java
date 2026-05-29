package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.query.DashboardDTO;
import cn.chyuan.ai.observability.domain.observe.service.DashboardService;
import cn.chyuan.ai.observability.infrastructure.redis.DashboardCacheService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import cn.chyuan.ai.observability.trigger.http.support.RequestValidator;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
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
    private final DashboardCacheService cacheService;

    public DashboardController(DashboardService dashboardService, DashboardCacheService cacheService) {
        this.dashboardService = dashboardService;
        this.cacheService = cacheService;
    }

    @GetMapping("/overview")
    public Response<DashboardDTO.Overview> overview(@RequestParam(defaultValue = "1") int days) {
        String validationError = RequestValidator.validateDays(days);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }

        String cacheKey = "overview:" + days;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            return Response.success(JSON.parseObject(cached, DashboardDTO.Overview.class));
        }

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

        cacheService.cache(cacheKey, JSON.toJSONString(overview));
        return Response.success(overview);
    }

    @GetMapping("/trend")
    public Response<List<Map<String, Object>>> trend(
            @RequestParam(defaultValue = "1") int days,
            @RequestParam(defaultValue = "hour") String interval) {
        String validationError = RequestValidator.validateDays(days);
        if (validationError == null) {
            validationError = RequestValidator.validateDashboardInterval(interval);
        }
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }

        String cacheKey = "trend:" + days + ":" + interval;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            return Response.success(JSON.parseObject(cached, new TypeReference<List<Map<String, Object>>>() {}));
        }

        String endTime = LocalDateTime.now().format(FMT);
        String startTime = LocalDateTime.now().minusDays(days).format(FMT);
        List<Map<String, Object>> result = dashboardService.getTrend(startTime, endTime, interval);
        cacheService.cache(cacheKey, JSON.toJSONString(result));
        return Response.success(result);
    }

    @GetMapping("/branch_distribution")
    public Response<List<Map<String, Object>>> branchDistribution(@RequestParam(defaultValue = "7") int days) {
        String validationError = RequestValidator.validateDays(days);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }

        String cacheKey = "branch:" + days;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            return Response.success(JSON.parseObject(cached, new TypeReference<List<Map<String, Object>>>() {}));
        }

        String endTime = LocalDateTime.now().format(FMT);
        String startTime = LocalDateTime.now().minusDays(days).format(FMT);
        List<Map<String, Object>> result = dashboardService.getBranchDistribution(startTime, endTime);
        cacheService.cache(cacheKey, JSON.toJSONString(result));
        return Response.success(result);
    }

    @GetMapping("/tool_usage")
    public Response<List<Map<String, Object>>> toolUsage(@RequestParam(defaultValue = "7") int days) {
        String validationError = RequestValidator.validateDays(days);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }

        String cacheKey = "tool:" + days;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            return Response.success(JSON.parseObject(cached, new TypeReference<List<Map<String, Object>>>() {}));
        }

        String endTime = LocalDateTime.now().format(FMT);
        String startTime = LocalDateTime.now().minusDays(days).format(FMT);
        List<Map<String, Object>> result = dashboardService.getToolUsage(startTime, endTime);
        cacheService.cache(cacheKey, JSON.toJSONString(result));
        return Response.success(result);
    }

    @GetMapping("/error_ranking")
    public Response<List<Map<String, Object>>> errorRanking(@RequestParam(defaultValue = "7") int days) {
        String validationError = RequestValidator.validateDays(days);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }

        String endTime = LocalDateTime.now().format(FMT);
        String startTime = LocalDateTime.now().minusDays(days).format(FMT);
        return Response.success(dashboardService.getErrorRanking(startTime, endTime));
    }
}
