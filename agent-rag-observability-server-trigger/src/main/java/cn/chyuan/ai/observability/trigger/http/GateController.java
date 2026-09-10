package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.evaluate.GateDTO;
import cn.chyuan.ai.observability.api.dto.evaluate.GateRecordDTO;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateRecordEntity;
import cn.chyuan.ai.observability.domain.evaluate.service.GateService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import cn.chyuan.ai.observability.trigger.http.support.RequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评测门禁控制器（工单 0136 R4）— 分层门禁规则 CRUD 与门禁记录查询：
 * <ul>
 *   <li>规则校验：safetyDims/scoreThresholds JSON 结构 + 维度存在性（GateService）</li>
 *   <li>记录查询：最新一条 / 历史列表（可按 gateId 过滤）/ 按回测任务</li>
 * </ul>
 * CI/CD 接入形态见 docs/02-agent-rag-observability-server/12-回测与分层门禁.md。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval/gate")
public class GateController {

    private final GateService gateService;

    public GateController(GateService gateService) {
        this.gateService = gateService;
    }

    @PostMapping
    public Response<String> create(@RequestBody GateDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "name 不能为空");
        }
        try {
            GateEntity entity = GateEntity.builder()
                    .name(dto.getName())
                    .safetyDimsJson(dto.getSafetyDimsJson())
                    .scoreThresholdsJson(dto.getScoreThresholdsJson())
                    .trials(dto.getTrials())
                    .enabled(dto.getEnabled())
                    .build();
            GateEntity created = gateService.create(entity);
            return Response.success(created.getGateId());
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @PostMapping("/update")
    public Response<String> update(@RequestBody GateDTO dto) {
        String validationError = RequestValidator.validateId("gateId", dto.getGateId());
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        try {
            GateEntity entity = GateEntity.builder()
                    .gateId(dto.getGateId())
                    .name(dto.getName())
                    .safetyDimsJson(dto.getSafetyDimsJson())
                    .scoreThresholdsJson(dto.getScoreThresholdsJson())
                    .trials(dto.getTrials())
                    .enabled(dto.getEnabled())
                    .build();
            gateService.update(entity);
            return Response.success("ok");
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    /** 删除 = 停用（历史门禁记录仍可追溯，不物理删除） */
    @PostMapping("/delete")
    public Response<String> delete(@RequestParam String gateId) {
        String validationError = RequestValidator.validateId("gateId", gateId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        try {
            gateService.delete(gateId);
            return Response.success("ok");
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @GetMapping("/list")
    public Response<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        String validationError = RequestValidator.validatePage(page, size);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        List<GateDTO> list = gateService.queryList(page, size).stream().map(this::toDto).toList();
        return Response.success(Map.of("list", list, "page", page, "size", size));
    }

    @GetMapping("/{gateId}")
    public Response<GateDTO> query(@PathVariable String gateId) {
        String validationError = RequestValidator.validateId("gateId", gateId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        GateEntity entity = gateService.query(gateId);
        return Response.success(entity == null ? null : toDto(entity));
    }

    // ===== 门禁记录查询（最新/历史/按任务） =====

    @GetMapping("/record/latest")
    public Response<GateRecordDTO> latestRecord(@RequestParam String gateId) {
        String validationError = RequestValidator.validateId("gateId", gateId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        return Response.success(toRecordDto(gateService.queryLatestRecord(gateId)));
    }

    @GetMapping("/record/list")
    public Response<Map<String, Object>> recordList(@RequestParam(required = false) String gateId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        String validationError = RequestValidator.validateOptionalId("gateId", gateId);
        if (validationError == null) {
            validationError = RequestValidator.validatePage(page, size);
        }
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        List<GateRecordDTO> list = gateService.queryRecordList(gateId, page, size).stream()
                .map(this::toRecordDto).toList();
        return Response.success(Map.of("list", list, "page", page, "size", size));
    }

    /** 按回测任务查判定记录（CI/CD 第二跳：任务 COMPLETED/FAILED 后查结论） */
    @GetMapping("/record/task/{taskId}")
    public Response<GateRecordDTO> recordByTask(@PathVariable String taskId) {
        String validationError = RequestValidator.validateId("taskId", taskId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        return Response.success(toRecordDto(gateService.queryRecordByTaskId(taskId)));
    }

    private GateDTO toDto(GateEntity entity) {
        return GateDTO.builder()
                .gateId(entity.getGateId()).name(entity.getName())
                .safetyDimsJson(entity.getSafetyDimsJson())
                .scoreThresholdsJson(entity.getScoreThresholdsJson())
                .trials(entity.getTrials()).enabled(entity.getEnabled())
                .createTime(entity.getCreateTime()).updateTime(entity.getUpdateTime())
                .build();
    }

    private GateRecordDTO toRecordDto(GateRecordEntity entity) {
        if (entity == null) {
            return null;
        }
        return GateRecordDTO.builder()
                .recordId(entity.getRecordId()).gateId(entity.getGateId())
                .taskId(entity.getTaskId()).result(entity.getResult())
                .triggerDetail(entity.getTriggerDetail()).createTime(entity.getCreateTime())
                .build();
    }
}
