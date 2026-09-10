package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.evaluate.EvalDatasetDTO;
import cn.chyuan.ai.observability.api.dto.evaluate.EvalResultDTO;
import cn.chyuan.ai.observability.api.dto.evaluate.EvalTaskDTO;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.service.EvaluateService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import cn.chyuan.ai.observability.trigger.http.support.RequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval")
public class EvaluateController {

    private final EvaluateService evaluateService;

    public EvaluateController(EvaluateService evaluateService) {
        this.evaluateService = evaluateService;
    }

    @PostMapping("/dataset")
    public Response<String> createDataset(@RequestBody EvalDatasetDTO dto) {
        EvalDatasetEntity entity = EvalDatasetEntity.builder()
                .datasetId(dto.getDatasetId()).datasetName(dto.getDatasetName())
                .description(dto.getDescription()).itemCount(dto.getItemCount())
                .itemsJson(dto.getItemsJson())
                .version(dto.getVersion()).pool(dto.getPool())
                .source(dto.getSource()).frozen(dto.getFrozen())
                .build();
        try {
            evaluateService.saveDataset(entity);
        } catch (IllegalArgumentException e) {
            // 工单 0134 R2：冻结不变式（frozen 版本条目不可改）等校验失败结构化拒绝
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
        return Response.success(dto.getDatasetId());
    }

    @GetMapping("/dataset/list")
    public Response<Map<String, Object>> listDatasets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String validationError = RequestValidator.validatePage(page, size);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        return Response.success(Map.of("list", evaluateService.queryDatasetList(page, size)));
    }

    // ========== 新增：三池筛选 + 版本化 + 冻结（工单 0134 R2） ==========

    /** 三池筛选：pool ∈ {golden, challenge, wrong, unclassified}（unclassified=存量未分类） */
    @GetMapping("/dataset/pool/{pool}")
    public Response<Map<String, Object>> listDatasetsByPool(
            @PathVariable String pool,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String validationError = RequestValidator.validatePage(page, size);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        try {
            return Response.success(Map.of("list", evaluateService.queryDatasetByPool(pool, page, size),
                    "page", page, "size", size));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    /** 版本列表：同名数据集的全部版本（版本号降序） */
    @GetMapping("/dataset/{datasetId}/versions")
    public Response<Map<String, Object>> listDatasetVersions(@PathVariable String datasetId) {
        String validationError = RequestValidator.validateId("datasetId", datasetId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        try {
            return Response.success(Map.of("list", evaluateService.queryDatasetVersions(datasetId)));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    /** 快照复制为新版本：条目原样复制，version 同名递增，新版本未冻结 */
    @PostMapping("/dataset/{datasetId}/copy-version")
    public Response<Map<String, Object>> copyDatasetVersion(@PathVariable String datasetId) {
        String validationError = RequestValidator.validateId("datasetId", datasetId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        try {
            EvalDatasetEntity copy = evaluateService.copyDatasetVersion(datasetId);
            return Response.success(Map.of(
                    "datasetId", copy.getDatasetId(),
                    "datasetName", copy.getDatasetName(),
                    "version", copy.getVersion(),
                    "frozen", copy.getFrozen()));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    /** 冻结版本（frozen 版本条目不可改） */
    @PostMapping("/dataset/{datasetId}/freeze")
    public Response<String> freezeDataset(@PathVariable String datasetId) {
        String validationError = RequestValidator.validateId("datasetId", datasetId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        try {
            evaluateService.freezeDataset(datasetId, true);
            return Response.success("ok");
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    /** 解冻版本（恢复条目可编辑） */
    @PostMapping("/dataset/{datasetId}/unfreeze")
    public Response<String> unfreezeDataset(@PathVariable String datasetId) {
        String validationError = RequestValidator.validateId("datasetId", datasetId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        try {
            evaluateService.freezeDataset(datasetId, false);
            return Response.success("ok");
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @PostMapping("/task")
    public Response<String> createTask(@RequestBody EvalTaskDTO dto) {
        EvalTaskEntity entity = EvalTaskEntity.builder()
                .taskName(dto.getTaskName()).evalType(dto.getEvalType())
                .datasetId(dto.getDatasetId()).modelVersion(dto.getModelVersion())
                .ragStrategyVersion(dto.getRagStrategyVersion())
                // Pass@k（工单 0135 R3）：trials 默认 1（服务层兜底）；阈值默认 0.5（执行层兜底）
                .trials(dto.getTrials()).passThreshold(dto.getPassThreshold())
                .build();
        String taskId = evaluateService.createTask(entity);
        return Response.success(taskId);
    }

    @GetMapping("/task/list")
    public Response<Map<String, Object>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String validationError = RequestValidator.validatePage(page, size);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        List<EvalTaskEntity> tasks = evaluateService.queryTaskList(page, size);
        List<EvalTaskDTO> dtoList = tasks.stream().map(this::toTaskDto).toList();
        return Response.success(Map.of("list", dtoList, "page", page, "size", size));
    }

    /** 任务实体 → DTO（含 Pass@k 汇总字段与回测 gate 绑定，工单 0135 R3 / 0136 R4） */
    private EvalTaskDTO toTaskDto(EvalTaskEntity entity) {
        return EvalTaskDTO.builder()
                .taskId(entity.getTaskId()).taskName(entity.getTaskName())
                .evalType(entity.getEvalType()).datasetId(entity.getDatasetId())
                .status(entity.getStatus()).modelVersion(entity.getModelVersion())
                .ragStrategyVersion(entity.getRagStrategyVersion())
                .totalCount(entity.getTotalCount()).completedCount(entity.getCompletedCount())
                .avgOverallScore(entity.getAvgOverallScore())
                .trials(entity.getTrials()).passThreshold(entity.getPassThreshold())
                .passRate(entity.getPassRate()).scoreStdDev(entity.getScoreStdDev())
                .gateId(entity.getGateId())
                .createTime(entity.getCreateTime()).updateTime(entity.getUpdateTime()).build();
    }

    @GetMapping("/task/{taskId}")
    public Response<EvalTaskDTO> queryTask(@PathVariable String taskId) {
        String validationError = RequestValidator.validateId("taskId", taskId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        EvalTaskEntity entity = evaluateService.queryTask(taskId);
        if (entity == null) return Response.success(null);
        return Response.success(toTaskDto(entity));
    }

    /** 保存评测结果（trialNo 随 per-trial 结果透传，工单 0135 R3） */
    @PostMapping("/result")
    public Response<String> saveResult(@RequestBody EvalResultDTO dto) {
        EvalResultEntity entity = EvalResultEntity.builder()
                .taskId(dto.getTaskId()).trialNo(dto.getTrialNo()).traceId(dto.getTraceId())
                .queryText(dto.getQueryText()).standardAnswer(dto.getStandardAnswer())
                .actualAnswer(dto.getActualAnswer()).recallScore(dto.getRecallScore())
                .precisionScore(dto.getPrecisionScore()).f1Score(dto.getF1Score())
                .top3HitRate(dto.getTop3HitRate()).mrrScore(dto.getMrrScore())
                .ndcgScore(dto.getNdcgScore()).mapScore(dto.getMapScore())
                .answerSimilarity(dto.getAnswerSimilarity())
                .contextPrecision(dto.getContextPrecision()).contextRecall(dto.getContextRecall())
                .contextRelevance(dto.getContextRelevance())
                .faithfulnessScore(dto.getFaithfulnessScore()).relevanceScore(dto.getRelevanceScore())
                .hallucinationFlag(dto.getHallucinationFlag()).completenessScore(dto.getCompletenessScore())
                .answerCorrectness(dto.getAnswerCorrectness())
                .overallScore(dto.getOverallScore()).evalDetail(dto.getEvalDetail())
                .toolSelectionScore(dto.getToolSelectionScore()).toolParamScore(dto.getToolParamScore())
                .toolCallScore(dto.getToolCallScore())
                .intentScore(dto.getIntentScore()).branchScore(dto.getBranchScore())
                .reasoningScore(dto.getReasoningScore()).agentDecisionScore(dto.getAgentDecisionScore())
                .build();
        evaluateService.saveResult(entity);
        return Response.success("ok");
    }

    @PostMapping("/task/{taskId}/run")
    public Response<String> runTask(@PathVariable String taskId) {
        String validationError = RequestValidator.validateId("taskId", taskId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        evaluateService.runTask(taskId);
        return Response.success(taskId);
    }

    /**
     * 查询评测结果（工单 0135 R3：可选 trial 过滤维度——
     * trial 缺省查全部 trial 的结果行；trial=1..k 查指定试验）。
     */
    @GetMapping("/result/{taskId}")
    public Response<Map<String, Object>> queryResults(@PathVariable String taskId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(required = false) Integer trial) {
        String validationError = RequestValidator.validateId("taskId", taskId);
        if (validationError == null) {
            validationError = RequestValidator.validatePage(page, size);
        }
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        if (trial != null && trial < 1) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "trial 必须 >= 1（试验序号 1..k）");
        }
        List<EvalResultEntity> results = evaluateService.queryResultsByTaskId(taskId, trial, page, size);
        long total = evaluateService.countResultsByTaskId(taskId);
        return Response.success(Map.of("list", results, "total", total, "page", page, "size", size));
    }

    @GetMapping("/result/compare")
    public Response<List<Map<String, Object>>> compareResults(
            @RequestParam String task1,
            @RequestParam String task2) {
        String validationError = RequestValidator.validateId("task1", task1);
        if (validationError == null) {
            validationError = RequestValidator.validateId("task2", task2);
        }
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        return Response.success(evaluateService.compareResults(task1, task2));
    }

    /**
     * 全局质量概览 — 聚合最近完成的评测任务，返回加权质量均值。
     * 供主页仪表盘「RAG 质量概览」面板使用。
     */
    @GetMapping("/quality_overview")
    public Response<Map<String, Object>> qualityOverview(
            @RequestParam(defaultValue = "10") int limit) {
        // limit 限制在合理范围，避免全表扫描
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return Response.success(evaluateService.computeGlobalAverages(safeLimit));
    }
}
