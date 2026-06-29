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
                .itemsJson(dto.getItemsJson()).build();
        evaluateService.saveDataset(entity);
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

    @PostMapping("/task")
    public Response<String> createTask(@RequestBody EvalTaskDTO dto) {
        EvalTaskEntity entity = EvalTaskEntity.builder()
                .taskName(dto.getTaskName()).evalType(dto.getEvalType())
                .datasetId(dto.getDatasetId()).modelVersion(dto.getModelVersion())
                .ragStrategyVersion(dto.getRagStrategyVersion()).build();
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
        List<EvalTaskDTO> dtoList = tasks.stream().map(entity -> EvalTaskDTO.builder()
                .taskId(entity.getTaskId()).taskName(entity.getTaskName())
                .evalType(entity.getEvalType()).datasetId(entity.getDatasetId())
                .status(entity.getStatus()).modelVersion(entity.getModelVersion())
                .ragStrategyVersion(entity.getRagStrategyVersion())
                .avgOverallScore(entity.getAvgOverallScore())
                .createTime(entity.getCreateTime()).updateTime(entity.getUpdateTime()).build()
        ).toList();
        return Response.success(Map.of("list", dtoList, "page", page, "size", size));
    }

    @GetMapping("/task/{taskId}")
    public Response<EvalTaskDTO> queryTask(@PathVariable String taskId) {
        String validationError = RequestValidator.validateId("taskId", taskId);
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        EvalTaskEntity entity = evaluateService.queryTask(taskId);
        if (entity == null) return Response.success(null);
        EvalTaskDTO dto = EvalTaskDTO.builder()
                .taskId(entity.getTaskId()).taskName(entity.getTaskName())
                .evalType(entity.getEvalType()).datasetId(entity.getDatasetId())
                .status(entity.getStatus()).modelVersion(entity.getModelVersion())
                .ragStrategyVersion(entity.getRagStrategyVersion())
                .avgOverallScore(entity.getAvgOverallScore())
                .createTime(entity.getCreateTime()).updateTime(entity.getUpdateTime()).build();
        return Response.success(dto);
    }

    @PostMapping("/result")
    public Response<String> saveResult(@RequestBody EvalResultDTO dto) {
        EvalResultEntity entity = EvalResultEntity.builder()
                .taskId(dto.getTaskId()).traceId(dto.getTraceId())
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

    @GetMapping("/result/{taskId}")
    public Response<Map<String, Object>> queryResults(@PathVariable String taskId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        String validationError = RequestValidator.validateId("taskId", taskId);
        if (validationError == null) {
            validationError = RequestValidator.validatePage(page, size);
        }
        if (validationError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, validationError);
        }
        List<EvalResultEntity> results = evaluateService.queryResultsByTaskId(taskId, page, size);
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
