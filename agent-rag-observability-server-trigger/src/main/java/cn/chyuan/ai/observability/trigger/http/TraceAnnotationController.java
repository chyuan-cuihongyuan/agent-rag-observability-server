package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.insight.model.entity.TraceAnnotationEntity;
import cn.chyuan.ai.observability.domain.insight.service.TraceAnnotationService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import cn.chyuan.ai.observability.trigger.http.support.RequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 人工评分注解控制器（工单 0150 U4）—
 * POST /api/v1/annotations（保存/upsert，operator 从 X-Operator 头透传）、
 * GET /api/v1/annotations?traceId=&operator=（单条）、GET /api/v1/annotations/list?score=&page=&size=（分页）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/annotations")
public class TraceAnnotationController {

    private final TraceAnnotationService traceAnnotationService;

    public TraceAnnotationController(TraceAnnotationService traceAnnotationService) {
        this.traceAnnotationService = traceAnnotationService;
    }

    /** 保存请求体 */
    public record SaveRequest(String traceId, Integer score, String note) {}

    @PostMapping
    public Response<Map<String, Object>> save(@RequestBody SaveRequest request,
                                              @org.springframework.web.bind.annotation.RequestHeader(
                                                      value = "X-Operator", required = false) String operator) {
        if (request == null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "请求体不能为空");
        }
        try {
            TraceAnnotationEntity saved = traceAnnotationService.save(
                    request.traceId(), request.score(), request.note(), operator);
            return Response.success(Map.of("id", saved.getId() == null ? 0L : saved.getId(),
                    "traceId", saved.getTraceId(),
                    "score", saved.getScore(),
                    "operator", saved.getOperator()));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @GetMapping
    public Response<TraceAnnotationEntity> query(@RequestParam String traceId,
                                                 @RequestParam(required = false) String operator) {
        String validationError = RequestValidator.validateId("traceId", traceId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        return Response.success(traceAnnotationService.queryByTraceAndOperator(traceId,
                operator == null || operator.isBlank() ? "unknown" : operator.trim()));
    }

    @GetMapping("/list")
    public Response<List<TraceAnnotationEntity>> list(@RequestParam(required = false) Integer score,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        try {
            return Response.success(traceAnnotationService.queryList(score, page, size));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }
}
