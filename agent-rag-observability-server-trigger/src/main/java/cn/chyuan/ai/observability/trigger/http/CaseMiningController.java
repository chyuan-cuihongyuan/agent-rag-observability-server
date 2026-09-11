package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.mining.CaseCandidateDTO;
import cn.chyuan.ai.observability.api.dto.mining.CaseDispositionRequestDTO;
import cn.chyuan.ai.observability.domain.mining.model.entity.CaseCandidateEntity;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseSource;
import cn.chyuan.ai.observability.domain.mining.service.CaseCandidateQueryService;
import cn.chyuan.ai.observability.domain.mining.service.CaseMiningService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Case 挖掘控制器（工单 0138 S2）— 候选查询、手动采集、批量回填错题集与忽略：
 * <ul>
 *   <li>GET /api/v1/eval/cases/candidates?source=&amp;page=&amp;size= — 候选列表（来源过滤）</li>
 *   <li>POST /api/v1/eval/cases/collect — 手动执行一轮三来源采集</li>
 *   <li>POST /api/v1/eval/cases/promote — 批量确认回填错题集（pool=wrong/source=trace/traceId 关联）</li>
 *   <li>POST /api/v1/eval/cases/ignore — 批量忽略</li>
 * </ul>
 * 自动模式（mining.auto-enabled 默认关）由 CaseMiningScheduler 承载。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval/cases")
public class CaseMiningController {

    private final CaseMiningService caseMiningService;
    private final CaseCandidateQueryService candidateQueryService;

    public CaseMiningController(CaseMiningService caseMiningService,
                                CaseCandidateQueryService candidateQueryService) {
        this.caseMiningService = caseMiningService;
        this.candidateQueryService = candidateQueryService;
    }

    @GetMapping("/candidates")
    public Response<List<CaseCandidateDTO>> candidates(@RequestParam(required = false) String source,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        CaseSource sourceEnum = null;
        if (source != null && !source.isBlank()) {
            sourceEnum = CaseSource.fromCode(source.trim());
            if (sourceEnum == null) {
                return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "非法来源: " + source
                        + "（合法值 EVAL_LOW_SCORE/TRACE_FAIL/PATROL_FAIL）");
            }
        }
        List<CaseCandidateDTO> list = candidateQueryService.queryList(sourceEnum, null, page, size).stream()
                .map(CaseMiningController::toDto)
                .collect(Collectors.toList());
        return Response.success(list);
    }

    @PostMapping("/collect")
    public Response<Map<String, Object>> collect(@RequestBody(required = false) Map<String, Object> config) {
        try {
            double threshold = config != null && config.get("lowScoreThreshold") instanceof Number n
                    ? n.doubleValue() : 0.6;
            int limit = config != null && config.get("scanLimit") instanceof Number n ? n.intValue() : 50;
            CaseMiningService.CollectOutcome outcome = caseMiningService.collect(CaseMiningService.MiningConfig.of(threshold, limit));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("evalLowScore", outcome.evalLowScore);
            data.put("traceFail", outcome.traceFail);
            data.put("patrolFail", outcome.patrolFail);
            data.put("total", outcome.total());
            return Response.success(data);
        } catch (Exception e) {
            log.error("手动采集失败", e);
            return Response.fail(ResponseCode.ERROR, "采集失败: " + e.getMessage());
        }
    }

    @PostMapping("/promote")
    public Response<Map<String, Object>> promote(@RequestBody CaseDispositionRequestDTO request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "ids 不能为空");
        }
        try {
            CaseMiningService.PromoteOutcome outcome = caseMiningService.promote(request.getIds(), request.getDatasetName());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("promoted", outcome.promoted());
            data.put("datasetId", outcome.datasetId());
            return Response.success(data);
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        } catch (Exception e) {
            log.error("回填失败", e);
            return Response.fail(ResponseCode.ERROR, "回填失败: " + e.getMessage());
        }
    }

    @PostMapping("/ignore")
    public Response<Map<String, Object>> ignore(@RequestBody CaseDispositionRequestDTO request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "ids 不能为空");
        }
        int ignored = caseMiningService.ignore(request.getIds());
        return Response.success(Map.of("ignored", ignored));
    }

    static CaseCandidateDTO toDto(CaseCandidateEntity e) {
        return CaseCandidateDTO.builder()
                .id(e.getId())
                .source(e.getSource() == null ? null : e.getSource().getCode())
                .sourceRef(e.getSourceRef())
                .traceId(e.getTraceId())
                .query(e.getQuery())
                .answerSummary(e.getAnswerSummary())
                .hitDocCount(e.getHitDocCount())
                .toolList(e.getToolList())
                .reason(e.getReason())
                .status(e.getStatus() == null ? null : e.getStatus().getCode())
                .promotedDatasetId(e.getPromotedDatasetId())
                .createTime(e.getCreateTime())
                .build();
    }
}
