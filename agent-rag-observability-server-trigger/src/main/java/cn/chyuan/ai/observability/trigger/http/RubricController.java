package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.evaluate.RubricDTO;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.RubricEntity;
import cn.chyuan.ai.observability.domain.evaluate.service.RubricService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import cn.chyuan.ai.observability.trigger.http.support.RequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Rubric 评判标准控制器（工单 0133 R1）— 评判标准配置化 CRUD：
 * 维度 key 唯一 / 权重和=1 / 非法 JSON 结构化拒绝；内置种子不可删改。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval/rubric")
public class RubricController {

    private final RubricService rubricService;

    public RubricController(RubricService rubricService) {
        this.rubricService = rubricService;
    }

    @PostMapping
    public Response<String> create(@RequestBody RubricDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "name 不能为空");
        }
        try {
            RubricEntity entity = RubricEntity.builder()
                    .name(dto.getName()).evalType(dto.getEvalType())
                    .version(dto.getVersion()).dimensionsJson(dto.getDimensionsJson())
                    .enabled(dto.getEnabled()).build();
            RubricEntity created = rubricService.create(entity);
            return Response.success(created.getRubricId());
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @PostMapping("/update")
    public Response<String> update(@RequestBody RubricDTO dto) {
        String validationError = RequestValidator.validateId("rubricId", dto.getRubricId());
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        try {
            RubricEntity entity = RubricEntity.builder()
                    .rubricId(dto.getRubricId()).name(dto.getName()).evalType(dto.getEvalType())
                    .version(dto.getVersion()).dimensionsJson(dto.getDimensionsJson())
                    .enabled(dto.getEnabled()).build();
            rubricService.update(entity);
            return Response.success("ok");
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @PostMapping("/delete")
    public Response<String> delete(@RequestParam String rubricId) {
        String validationError = RequestValidator.validateId("rubricId", rubricId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        try {
            rubricService.delete(rubricId);
            return Response.success("ok");
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @GetMapping("/list")
    public Response<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String validationError = RequestValidator.validatePage(page, size);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        List<RubricDTO> list = rubricService.queryList(page, size).stream()
                .map(this::toDto).toList();
        return Response.success(Map.of("list", list, "page", page, "size", size));
    }

    @GetMapping("/{rubricId}")
    public Response<RubricDTO> query(@PathVariable String rubricId) {
        String validationError = RequestValidator.validateId("rubricId", rubricId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        RubricEntity entity = rubricService.query(rubricId);
        return Response.success(entity == null ? null : toDto(entity));
    }

    private RubricDTO toDto(RubricEntity entity) {
        return RubricDTO.builder()
                .rubricId(entity.getRubricId()).name(entity.getName())
                .evalType(entity.getEvalType()).version(entity.getVersion())
                .dimensionsJson(entity.getDimensionsJson()).enabled(entity.getEnabled())
                .builtin(entity.getBuiltin())
                .createTime(entity.getCreateTime()).updateTime(entity.getUpdateTime())
                .build();
    }
}
