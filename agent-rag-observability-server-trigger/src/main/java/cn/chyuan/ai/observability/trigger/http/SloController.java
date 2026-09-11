package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.alert.service.AlertSilenceService;
import cn.chyuan.ai.observability.domain.insight.service.SloBurnRateService;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SLO 燃烧率控制器（工单 0183 Y7）—
 * GET /api/v1/insight/slo：基于 chat_result_log 成败计数计算 1h/6h/3d 三窗燃烧率与分级；
 * PAGE/TICKET 事件先过告警静默（0179），命中静默仅记日志不外发。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/insight/slo")
public class SloController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IChatResultRepository chatResultRepository;
    private final SloBurnRateService sloBurnRateService;
    private final AlertSilenceService alertSilenceService;

    public SloController(IChatResultRepository chatResultRepository,
                         SloBurnRateService sloBurnRateService,
                         AlertSilenceService alertSilenceService) {
        this.chatResultRepository = chatResultRepository;
        this.sloBurnRateService = sloBurnRateService;
        this.alertSilenceService = alertSilenceService;
    }

    @GetMapping
    public Response<Map<String, Object>> slo() {
        Map<String, long[]> counts = new LinkedHashMap<>();
        counts.put("1h", windowCount(0, 1));
        counts.put("6h", windowCount(0, 6));
        counts.put("3d", windowCount(0, 72));
        Map<String, Object> result = sloBurnRateService.evaluate(counts);

        String severity = String.valueOf(result.get("severity"));
        if (!"NONE".equals(severity)) {
            String eventKey = "slo.burn_rate";
            if (alertSilenceService.isSilenced(eventKey)) {
                log.info("SLO 事件被静默抑制: severity={}, key={}", severity, eventKey);
                result.put("suppressed", true);
            } else {
                log.warn("SLO_BURN_RATE severity={} detail={}", severity, result);
                result.put("suppressed", false);
            }
        }
        return Response.success(result);
    }

    /** 窗口成败计数：起始时间点到现在，FAIL/TIMEOUT 计坏 */
    private long[] windowCount(int fromDaysAgo, int hours) {
        String start = FMT.format(LocalDateTime.now().minusHours(hours).minusDays(fromDaysAgo));
        String end = FMT.format(LocalDateTime.now());
        long total = chatResultRepository.countByStatus("SUCCESS", start, end)
                + chatResultRepository.countByStatus("FAIL", start, end)
                + chatResultRepository.countByStatus("TIMEOUT", start, end);
        long bad = chatResultRepository.countByStatus("FAIL", start, end)
                + chatResultRepository.countByStatus("TIMEOUT", start, end);
        return new long[]{total, bad};
    }
}
