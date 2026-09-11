package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.evaluate.service.EloRatingService;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Elo 评分控制器（工单 0171 X2）—
 * GET /api/v1/eval/elo（当前排行，由对局历史即时重算）、
 * POST /api/v1/eval/elo/recalculate（显式重算，幂等，返回同一排行）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval/elo")
public class EloController {

    private final EloRatingService eloRatingService;

    public EloController(EloRatingService eloRatingService) {
        this.eloRatingService = eloRatingService;
    }

    @GetMapping
    public Response<Map<String, Double>> ranking() {
        return Response.success(eloRatingService.recalculate());
    }

    @PostMapping("/recalculate")
    public Response<Map<String, Double>> recalculate() {
        return Response.success(eloRatingService.recalculate());
    }
}
