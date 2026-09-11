package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.patrol.PatrolRecordDTO;
import cn.chyuan.ai.observability.api.dto.patrol.PatrolRoundSummaryDTO;
import cn.chyuan.ai.observability.domain.patrol.model.entity.PatrolRecordEntity;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundConfig;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundSummary;
import cn.chyuan.ai.observability.domain.patrol.service.PatrolProbeService;
import cn.chyuan.ai.observability.domain.patrol.service.PatrolQueryService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 巡检拨测控制器（工单 0137 S1）— 查询与手动触发：
 * <ul>
 *   <li>GET /api/v1/patrol/records — 拨测记录历史（分页）</li>
 *   <li>GET /api/v1/patrol/latest — 最近一轮汇总</li>
 *   <li>POST /api/v1/patrol/trigger — 立即执行一轮（同步返回汇总，运维拨测用）</li>
 * </ul>
 * 触发端点与调度器（PatrolScheduler）读同一组 patrol.* 配置属性，语义一致；
 * 在线评测三手段中本模块承载「巡检」（AB/影子出界，见 0118 调研与 0120 D4 裁定）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/patrol")
public class PatrolController {

    private final PatrolProbeService patrolProbeService;
    private final PatrolQueryService patrolQueryService;

    @Value("${patrol.timeout-ms:30000}")
    private long timeoutMs;

    @Value("${patrol.agent-id:}")
    private String agentId;

    @Value("${patrol.dataset-id:}")
    private String datasetId;

    @Value("${patrol.queries:}")
    private String queriesCsv;

    public PatrolController(PatrolProbeService patrolProbeService, PatrolQueryService patrolQueryService) {
        this.patrolProbeService = patrolProbeService;
        this.patrolQueryService = patrolQueryService;
    }

    @GetMapping("/records")
    public Response<List<PatrolRecordDTO>> records(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        List<PatrolRecordDTO> records = patrolQueryService.queryRecords(page, size).stream()
                .map(PatrolController::toDto)
                .collect(Collectors.toList());
        return Response.success(records);
    }

    @GetMapping("/latest")
    public Response<PatrolRoundSummaryDTO> latest() {
        return Response.success(toDto(patrolQueryService.queryLatestRound()));
    }

    /** 手动触发一轮拨测（配置语义与定时调度一致，便于运维即时验证） */
    @PostMapping("/trigger")
    public Response<PatrolRoundSummaryDTO> trigger() {
        try {
            PatrolRoundSummary summary = patrolProbeService.runRound(buildConfig());
            return Response.success(toDto(summary));
        } catch (Exception e) {
            log.error("手动触发巡检失败", e);
            return Response.fail(ResponseCode.ERROR, "巡检执行失败: " + e.getMessage());
        }
    }

    /** 轮次配置装配（与 PatrolScheduler 同一组属性，重复四行以避免跨 Bean 条件装配复杂度） */
    private PatrolRoundConfig buildConfig() {
        List<String> queries = queriesCsv == null || queriesCsv.isBlank()
                ? List.of()
                : Arrays.stream(queriesCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        return PatrolRoundConfig.builder()
                .timeoutMs(timeoutMs)
                .agentId(blankToNull(agentId))
                .queries(queries)
                .datasetId(blankToNull(datasetId))
                .build();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    static PatrolRecordDTO toDto(PatrolRecordEntity e) {
        return PatrolRecordDTO.builder()
                .id(e.getId())
                .roundId(e.getRoundId())
                .taskRef(e.getTaskRef())
                .query(e.getQuery())
                .agentId(e.getAgentId())
                .status(e.getStatus() == null ? null : e.getStatus().getCode())
                .score(e.getScore())
                .durationMs(e.getDurationMs())
                .errorSummary(e.getErrorSummary())
                .traceId(e.getTraceId())
                .createTime(e.getCreateTime())
                .build();
    }

    static PatrolRoundSummaryDTO toDto(PatrolRoundSummary s) {
        return PatrolRoundSummaryDTO.builder()
                .roundId(s.getRoundId())
                .total(s.getTotal())
                .success(s.getSuccess())
                .fail(s.getFail())
                .timeout(s.getTimeout())
                .avgScore(s.getAvgScore())
                .finishedAt(s.getFinishedAt())
                .build();
    }
}
