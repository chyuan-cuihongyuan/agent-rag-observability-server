package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.alert.model.entity.AlertSilenceEntity;
import cn.chyuan.ai.observability.domain.alert.service.AlertSilenceService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 告警静默控制器（工单 0179 Y3）—
 * POST /api/v1/alerts/silences（新建静默窗）、GET /api/v1/alerts/silences（列表）、
 * DELETE /api/v1/alerts/silences/{id}（删除）、GET /api/v1/alerts/silences/match?key=（评估某告警键）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/alerts/silences")
public class AlertSilenceController {

    private final AlertSilenceService alertSilenceService;

    public AlertSilenceController(AlertSilenceService alertSilenceService) {
        this.alertSilenceService = alertSilenceService;
    }

    @PostMapping
    public Response<AlertSilenceEntity> create(@RequestParam String silenceKey,
                                               @RequestParam String startsAt,
                                               @RequestParam String endsAt,
                                               @RequestParam(required = false) String createdBy,
                                               @RequestParam(required = false) String reason) {
        try {
            return Response.success(alertSilenceService.create(silenceKey, startsAt, endsAt, createdBy, reason));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @GetMapping
    public Response<List<AlertSilenceEntity>> list() {
        return Response.success(alertSilenceService.list());
    }

    @DeleteMapping("/{id}")
    public Response<Map<String, Object>> delete(@PathVariable long id) {
        return Response.success(Map.of("deleted", alertSilenceService.delete(id)));
    }

    @GetMapping("/match")
    public Response<Map<String, Object>> match(@RequestParam String key) {
        return Response.success(Map.of("key", key, "silenced", alertSilenceService.isSilenced(key)));
    }
}
