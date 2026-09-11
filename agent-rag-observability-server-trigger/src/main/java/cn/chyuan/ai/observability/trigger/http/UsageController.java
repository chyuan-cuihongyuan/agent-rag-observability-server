package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.usage.service.UserUsageService;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 用户/租户使用分析控制器（工单 0149 U3）—
 * GET /api/v1/usage/users/topN?days=&topN=（调用量/失败率/平均耗时排行）、
 * GET /api/v1/usage/tenants/summary?days=（租户维度汇总）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/usage")
public class UsageController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IChatResultRepository chatResultRepository;
    private final UserUsageService userUsageService;

    public UsageController(IChatResultRepository chatResultRepository, UserUsageService userUsageService) {
        this.chatResultRepository = chatResultRepository;
        this.userUsageService = userUsageService;
    }

    @GetMapping("/users/topN")
    public Response<List<Map<String, Object>>> topUsers(@RequestParam(defaultValue = "7") int days,
                                                        @RequestParam(defaultValue = "10") int topN) {
        return Response.success(userUsageService.topUsers(loadSources(days), topN));
    }

    @GetMapping("/tenants/summary")
    public Response<List<Map<String, Object>>> tenantSummary(@RequestParam(defaultValue = "7") int days) {
        return Response.success(userUsageService.tenantSummary(loadSources(days)));
    }

    private List<cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity> loadSources(int days) {
        int d = Math.min(Math.max(days, 1), 90);
        String start = FMT.format(LocalDateTime.now().minusDays(d));
        return chatResultRepository.queryCostSources(start, 5000);
    }
}
