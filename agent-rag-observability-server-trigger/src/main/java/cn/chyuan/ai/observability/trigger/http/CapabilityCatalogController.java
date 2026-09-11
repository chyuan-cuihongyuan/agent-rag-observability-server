package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.catalog.service.CapabilityCatalogService;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 能力目录计分卡控制器（工单 0184 Z1）—
 * GET /api/v1/catalog/scorecard：静态能力清单 × 动态健康面 → 每服务状态与分数 + 总分。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/catalog")
public class CapabilityCatalogController {

    private final CapabilityCatalogService capabilityCatalogService;

    public CapabilityCatalogController(CapabilityCatalogService capabilityCatalogService) {
        this.capabilityCatalogService = capabilityCatalogService;
    }

    @GetMapping("/scorecard")
    public Response<Map<String, Object>> scorecard() {
        return Response.success(capabilityCatalogService.buildScorecard());
    }
}
