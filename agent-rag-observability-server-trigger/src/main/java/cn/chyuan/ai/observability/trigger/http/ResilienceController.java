package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.resilience.service.BackfillJob;
import cn.chyuan.ai.observability.domain.resilience.service.BackfillPlanner;
import cn.chyuan.ai.observability.domain.resilience.service.LagSnapshot;
import cn.chyuan.ai.observability.domain.resilience.service.QualityRule;
import cn.chyuan.ai.observability.domain.resilience.service.QualityRunner;
import cn.chyuan.ai.observability.domain.resilience.service.ResilienceService;
import cn.chyuan.ai.observability.domain.resilience.service.SlaMiss;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度韧性控制器（五期 AD 簇 0220-0223）—
 * 延迟：GET /api/v1/resilience/lag（即时采样）/ GET /api/v1/resilience/lag/recent；
 * 质量：POST/GET/DELETE /api/v1/resilience/quality/rules、POST /api/v1/resilience/quality/run、
 * GET /api/v1/resilience/quality/results；
 * 回填：POST /api/v1/resilience/backfill（创建）、POST /api/v1/resilience/backfill/{id}/run（幂等执行）、
 * GET /api/v1/resilience/backfill/{id}；
 * SLA：POST /api/v1/resilience/sla/judge、GET /api/v1/resilience/sla/misses。
 * MQ 延迟端口经 {@link ResilienceService.MqLagPort} 注入（无适配时采样端点返回提示）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/resilience")
public class ResilienceController {

    private final ResilienceService resilienceService;
    private final ResilienceService.MqLagPort mqLagPort;

    public ResilienceController(ResilienceService resilienceService,
            @Autowired(required = false) ResilienceService.MqLagPort mqLagPort) {
        this.resilienceService = resilienceService;
        this.mqLagPort = mqLagPort;
    }

    // ── AD1 延迟 ──

    @GetMapping("/lag")
    public Response<Map<String, Object>> sampleLag() {
        if (mqLagPort == null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "未配置 MQ 延迟适配（IMqLagPort/MqLagPort）");
        }
        return Response.success(resilienceService.sampleLag(mqLagPort, System.currentTimeMillis()));
    }

    @GetMapping("/lag/recent")
    public Response<List<Map<String, Object>>> recentLags(@RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> out = resilienceService.recentLags(limit).stream()
                .map(LagSnapshot::toMap).toList();
        return Response.success(out);
    }

    // ── AD2 质量 ──

    @PostMapping("/quality/rules")
    public Response<Map<String, Object>> upsertRule(@RequestBody Map<String, Object> body) {
        try {
            String name = String.valueOf(body.get("name"));
            @SuppressWarnings("unchecked")
            Map<String, String> params = body.get("params") instanceof Map<?, ?> m
                    ? (Map<String, String>) m : Map.of();
            QualityRule rule = new QualityRule(null, name, str(body.get("target")),
                    str(body.get("field")), str(body.get("type")), params, true);
            resilienceService.upsertRule(rule);
            return Response.success(Map.of("name", name, "upserted", true));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @GetMapping("/quality/rules")
    public Response<List<QualityRule>> listRules() {
        return Response.success(resilienceService.listRules());
    }

    @DeleteMapping("/quality/rules/{name}")
    public Response<Map<String, Object>> deleteRule(@PathVariable String name) {
        return Response.success(Map.of("deleted", resilienceService.deleteRule(name)));
    }

    /** 行集求值：body.rows = List<Map<field, value>>（数据面装配端口接入前由调用方组装） */
    @PostMapping("/quality/run")
    public Response<QualityRunner.QualityResult> runQuality(@RequestBody Map<String, Object> body) {
        try {
            String ruleName = str(body.get("ruleName"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = body.get("rows") instanceof List<?> l
                    ? (List<Map<String, Object>>) l : List.of();
            return Response.success(resilienceService.runQuality(ruleName, rows, System.currentTimeMillis()));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @GetMapping("/quality/results")
    public Response<List<Map<String, Object>>> recentResults(@RequestParam(defaultValue = "20") int limit) {
        return Response.success(resilienceService.recentResults(limit).stream()
                .map(QualityRunner.QualityResult::toMap).toList());
    }

    // ── AD3 回填 ──

    @PostMapping("/backfill")
    public Response<BackfillJob> createBackfill(@RequestBody Map<String, Object> body) {
        try {
            BackfillJob job = resilienceService.createBackfill(str(body.get("name")),
                    Long.parseLong(str(body.get("rangeStart"))),
                    Long.parseLong(str(body.get("rangeEnd"))),
                    Integer.parseInt(str(body.getOrDefault("shardCount", "4"))),
                    System.currentTimeMillis());
            return Response.success(job);
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    /** 幂等执行：分片函数内置为日志桩（真实数据面由 ShardFn 注入），演示断点续跑语义 */
    @PostMapping("/backfill/{id}/run")
    public Response<BackfillJob> runBackfill(@PathVariable String id) {
        try {
            BackfillJob job = resilienceService.runBackfill(id, shard ->
                    log.info("回填分片执行: job={} shard={} range=[{},{}]", id, shard.index(),
                            shard.start(), shard.end()), System.currentTimeMillis());
            return Response.success(job);
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @GetMapping("/backfill/{id}")
    public Response<BackfillJob> backfillDetail(@PathVariable String id) {
        BackfillJob job = resilienceService.findBackfill(id);
        if (job == null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "回填任务不存在: " + id);
        }
        return Response.success(job);
    }

    // ── AD4 SLA ──

    @PostMapping("/sla/judge")
    public Response<Map<String, Object>> judgeSla(@RequestBody Map<String, Object> body) {
        try {
            SlaMiss miss = resilienceService.judgeSla(str(body.get("task")),
                    Long.parseLong(str(body.get("expectedMs"))),
                    Long.parseLong(str(body.get("actualMs"))),
                    System.currentTimeMillis());
            Map<String, Object> out = new HashMap<>();
            out.put("missed", miss != null);
            out.put("miss", miss);
            return Response.success(out);
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @GetMapping("/sla/misses")
    public Response<List<SlaMiss>> recentMisses(@RequestParam(defaultValue = "50") int limit) {
        return Response.success(resilienceService.recentSlaMisses(limit));
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
