package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class EvaluateService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IEvalTaskRepository evalTaskRepository;
    private final IEvalResultRepository evalResultRepository;
    private final IEvalDatasetRepository evalDatasetRepository;

    @Resource
    private EvalExecutionService evalExecutionService;

    @Resource
    private EvalConcurrencyGuard evalConcurrencyGuard;

    public EvaluateService(IEvalTaskRepository evalTaskRepository,
                           IEvalResultRepository evalResultRepository,
                           IEvalDatasetRepository evalDatasetRepository) {
        this.evalTaskRepository = evalTaskRepository;
        this.evalResultRepository = evalResultRepository;
        this.evalDatasetRepository = evalDatasetRepository;
    }

    public String createTask(EvalTaskEntity entity) {
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        entity.setTaskId(taskId);
        entity.setStatus("PENDING");
        entity.setTotalCount(entity.getTotalCount() == null ? 0 : entity.getTotalCount());
        entity.setCompletedCount(entity.getCompletedCount() == null ? 0 : entity.getCompletedCount());
        entity.setModelVersion(entity.getModelVersion() == null ? "" : entity.getModelVersion());
        entity.setRagStrategyVersion(entity.getRagStrategyVersion() == null ? "" : entity.getRagStrategyVersion());
        entity.setCreateTime(LocalDateTime.now().format(FMT));
        entity.setUpdateTime(LocalDateTime.now().format(FMT));
        evalTaskRepository.save(entity);
        return taskId;
    }

    public EvalTaskEntity queryTask(String taskId) {
        return evalTaskRepository.queryByTaskId(taskId);
    }

    public List<EvalTaskEntity> queryTaskList(int page, int size) {
        return evalTaskRepository.queryList(page, size);
    }

    public void updateTaskStatus(String taskId, String status) {
        evalTaskRepository.updateStatus(taskId, status);
    }

    public void saveResult(EvalResultEntity entity) {
        entity.setCreateTime(LocalDateTime.now().format(FMT));
        evalResultRepository.save(entity);
    }

    public void batchSaveResults(List<EvalResultEntity> entities) {
        String now = LocalDateTime.now().format(FMT);
        entities.forEach(e -> e.setCreateTime(now));
        evalResultRepository.batchSave(entities);
    }

    public List<EvalResultEntity> queryResultsByTaskId(String taskId, int page, int size) {
        return evalResultRepository.queryByTaskId(taskId, page, size);
    }

    public long countResultsByTaskId(String taskId) {
        return evalResultRepository.countByTaskId(taskId);
    }

    public void saveDataset(EvalDatasetEntity entity) {
        evalDatasetRepository.save(entity);
    }

    public EvalDatasetEntity queryDataset(String datasetId) {
        return evalDatasetRepository.queryByDatasetId(datasetId);
    }

    public List<EvalDatasetEntity> queryDatasetList(int page, int size) {
        return evalDatasetRepository.queryList(page, size);
    }

    /**
     * 异步执行评测任务 — 提交到 observeExecutor 线程池执行 EvalExecutionService。
     * 立即返回，让前端轮询进度。并发闸门饱和时立即拒绝（FAILED），不放行不排队。
     */
    @Async("observeExecutor")
    public void runTask(String taskId) {
        EvalTaskEntity task = evalTaskRepository.queryByTaskId(taskId);
        if (task == null) {
            return;
        }
        if (!evalConcurrencyGuard.tryAcquire()) {
            log.warn("评测并发已满，任务直接拒绝: taskId={}", taskId);
            evalTaskRepository.updateStatus(taskId, "FAILED");
            return;
        }
        try {
            evalTaskRepository.updateStatus(taskId, "RUNNING");
            evalExecutionService.execute(task);
        } catch (Exception e) {
            evalTaskRepository.updateStatus(taskId, "FAILED");
        } finally {
            evalConcurrencyGuard.release();
        }
    }

    public List<Map<String, Object>> compareResults(String task1, String task2) {
        return List.of(
                buildCompareMap(task1, computeTaskAverages(task1)),
                buildCompareMap(task2, computeTaskAverages(task2))
        );
    }

    /**
     * 全局质量聚合 — 取最近 N 个 COMPLETED 任务，按样本数加权计算质量均值。
     * 用于主页仪表盘「RAG 质量概览」面板。
     *
     * @param limit 取最近多少个完成任务（默认 10）
     * @return 质量均值 + taskCount/sampleCount/updateTime；无数据时各值为 0
     */
    public Map<String, Object> computeGlobalAverages(int limit) {
        List<EvalTaskEntity> recent = evalTaskRepository.queryRecentCompleted(limit);
        Map<String, Object> map = new HashMap<>();

        if (recent.isEmpty()) {
            map.put("taskCount", 0);
            map.put("sampleCount", 0);
            return map;
        }

        // 按样本数加权累加：每个任务的指标 × 样本数，最后除以总样本数
        long totalSamples = 0;
        double wOverall = 0, wRecall = 0, wFaith = 0, wPrecision = 0, wMrr = 0, wNdcg = 0;
        double wCtxP = 0, wCtxR = 0, wCtxRel = 0, wCorrect = 0;
        String latestUpdateTime = "";

        for (EvalTaskEntity task : recent) {
            TaskAverages avg = computeTaskAverages(task.getTaskId());
            long c = avg.count;
            if (c <= 0) continue;
            totalSamples += c;
            wOverall += avg.avgOverall * c;
            wRecall += avg.avgRecall * c;
            wFaith += avg.avgFaith * c;
            wPrecision += avg.avgPrecision * c;
            wMrr += avg.avgMrr * c;
            wNdcg += avg.avgNdcg * c;
            wCtxP += avg.avgContextPrecision * c;
            wCtxR += avg.avgContextRecall * c;
            wCtxRel += avg.avgContextRelevance * c;
            wCorrect += avg.avgAnswerCorrectness * c;
            // 取最新的 updateTime（任务已按 update_time 降序，第一个即最新）
            if (latestUpdateTime.isEmpty()) {
                latestUpdateTime = task.getUpdateTime() == null ? "" : task.getUpdateTime();
            }
        }

        if (totalSamples > 0) {
            map.put("avgOverallScore", round(wOverall / totalSamples));
            map.put("avgRecallScore", round(wRecall / totalSamples));
            map.put("avgFaithfulnessScore", round(wFaith / totalSamples));
            map.put("avgPrecisionScore", round(wPrecision / totalSamples));
            map.put("avgMrrScore", round(wMrr / totalSamples));
            map.put("avgNdcgScore", round(wNdcg / totalSamples));
            map.put("avgContextPrecision", round(wCtxP / totalSamples));
            map.put("avgContextRecall", round(wCtxR / totalSamples));
            map.put("avgContextRelevance", round(wCtxRel / totalSamples));
            map.put("avgAnswerCorrectness", round(wCorrect / totalSamples));
        }
        map.put("taskCount", recent.size());
        map.put("sampleCount", totalSamples);
        map.put("updateTime", latestUpdateTime);
        return map;
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /**
     * 分页查询全部结果并计算平均值，避免硬编码只取前 100 条
     */
    private TaskAverages computeTaskAverages(String taskId) {
        double sumOverall = 0, sumRecall = 0, sumFaith = 0;
        double sumPrecision = 0, sumMrr = 0, sumNdcg = 0;
        double sumContextPrecision = 0, sumContextRecall = 0, sumContextRelevance = 0;
        double sumAnswerCorrectness = 0;
        int count = 0;
        int page = 1;
        int pageSize = 500;
        List<EvalResultEntity> batch;

        do {
            batch = evalResultRepository.queryByTaskId(taskId, page, pageSize);
            for (EvalResultEntity r : batch) {
                sumOverall += nz(r.getOverallScore());
                sumRecall += nz(r.getRecallScore());
                sumFaith += nz(r.getFaithfulnessScore());
                sumPrecision += nz(r.getPrecisionScore());
                sumMrr += nz(r.getMrrScore());
                sumNdcg += nz(r.getNdcgScore());
                sumContextPrecision += nz(r.getContextPrecision());
                sumContextRecall += nz(r.getContextRecall());
                sumContextRelevance += nz(r.getContextRelevance());
                sumAnswerCorrectness += nz(r.getAnswerCorrectness());
                count++;
            }
            page++;
        } while (batch.size() == pageSize); // 批次未满说明已读完

        TaskAverages avg = new TaskAverages();
        if (count > 0) {
            double c = count;
            avg.avgOverall = sumOverall / c;
            avg.avgRecall = sumRecall / c;
            avg.avgFaith = sumFaith / c;
            avg.avgPrecision = sumPrecision / c;
            avg.avgMrr = sumMrr / c;
            avg.avgNdcg = sumNdcg / c;
            avg.avgContextPrecision = sumContextPrecision / c;
            avg.avgContextRecall = sumContextRecall / c;
            avg.avgContextRelevance = sumContextRelevance / c;
            avg.avgAnswerCorrectness = sumAnswerCorrectness / c;
        }
        avg.count = count;
        return avg;
    }

    /** null 视为 0，避免 NPE */
    private double nz(Double v) {
        return v != null ? v : 0.0;
    }

    private Map<String, Object> buildCompareMap(String taskId, TaskAverages avg) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", taskId);
        map.put("avgOverallScore", avg.avgOverall);
        map.put("avgRecallScore", avg.avgRecall);
        map.put("avgFaithfulnessScore", avg.avgFaith);
        map.put("avgPrecisionScore", avg.avgPrecision);
        map.put("avgMrrScore", avg.avgMrr);
        map.put("avgNdcgScore", avg.avgNdcg);
        map.put("avgContextPrecision", avg.avgContextPrecision);
        map.put("avgContextRecall", avg.avgContextRecall);
        map.put("avgContextRelevance", avg.avgContextRelevance);
        map.put("avgAnswerCorrectness", avg.avgAnswerCorrectness);
        map.put("count", avg.count);
        return map;
    }

    /** 内部聚合结果 */
    private static class TaskAverages {
        double avgOverall;
        double avgRecall;
        double avgFaith;
        double avgPrecision;
        double avgMrr;
        double avgNdcg;
        double avgContextPrecision;
        double avgContextRecall;
        double avgContextRelevance;
        double avgAnswerCorrectness;
        int count;
    }
}
