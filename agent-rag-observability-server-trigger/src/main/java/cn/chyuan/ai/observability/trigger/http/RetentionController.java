package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.insight.service.RetentionService;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 保留清理控制器（工单 0152 U6）— dry-run 统计与手动触发：
 * GET /api/v1/retention/dry-run?days=（只统计不删）、POST /api/v1/retention/purge?days=（手动清理一批窗口）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/retention")
public class RetentionController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RetentionService retentionService;

    public RetentionController(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @GetMapping("/dry-run")
    public Response<Map<String, Long>> dryRun(@RequestParam(defaultValue = "90") int days) {
        int d = Math.min(Math.max(days, 1), 3650);
        return Response.success(retentionService.dryRun(LocalDateTime.now().minusDays(d)));
    }

    @PostMapping("/purge")
    public Response<Map<String, Long>> purge(@RequestParam(defaultValue = "90") int days) {
        int d = Math.min(Math.max(days, 1), 3650);
        return Response.success(retentionService.purge(LocalDateTime.now().minusDays(d)));
    }
}
