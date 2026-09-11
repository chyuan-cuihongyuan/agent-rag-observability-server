package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.cost.service.CostDeriveService;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
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
 * 成本看板控制器（工单 0148 U2，借鉴 LiteLLM spend）—
 * GET /api/v1/cost/daily?days=（按天成本/Token 趋势）、/api/v1/cost/byAgent?days=（按 agent 成本排行）、
 * /api/v1/cost/unpriced?days=（未配置计价的请求数，提示补计价表）。
 * 读时派生口径：按计价表即时计算，不依赖落库 cost 列（覆盖存量数据）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/cost")
public class CostController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IChatResultRepository chatResultRepository;
    private final CostDeriveService costDeriveService;

    public CostController(IChatResultRepository chatResultRepository, CostDeriveService costDeriveService) {
        this.chatResultRepository = chatResultRepository;
        this.costDeriveService = costDeriveService;
    }

    @GetMapping("/daily")
    public Response<List<Map<String, Object>>> daily(@RequestParam(defaultValue = "7") int days) {
        return Response.success(costDeriveService.aggregateDaily(loadSources(days)));
    }

    @GetMapping("/byAgent")
    public Response<List<Map<String, Object>>> byAgent(@RequestParam(defaultValue = "7") int days) {
        return Response.success(costDeriveService.aggregateByAgent(loadSources(days)));
    }

    @GetMapping("/unpriced")
    public Response<Map<String, Object>> unpriced(@RequestParam(defaultValue = "7") int days) {
        return Response.success(Map.of("unpricedRequests", costDeriveService.countUnpriced(loadSources(days))));
    }

    private List<cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity> loadSources(int days) {
        int d = Math.min(Math.max(days, 1), 90);
        String start = FMT.format(LocalDateTime.now().minusDays(d));
        return chatResultRepository.queryCostSources(start, 5000);
    }
}
