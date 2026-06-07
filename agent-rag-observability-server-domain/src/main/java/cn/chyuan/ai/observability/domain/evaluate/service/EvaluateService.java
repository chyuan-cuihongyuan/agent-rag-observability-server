package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EvaluateService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IEvalTaskRepository evalTaskRepository;
    private final IEvalResultRepository evalResultRepository;
    private final IEvalDatasetRepository evalDatasetRepository;

    @Resource
    private EvalExecutionService evalExecutionService;

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
     * 立即返回，让前端轮询进度。
     */
    @Async("observeExecutor")
    public void runTask(String taskId) {
        EvalTaskEntity task = evalTaskRepository.queryByTaskId(taskId);
        if (task == null) {
            return;
        }
        evalTaskRepository.updateStatus(taskId, "RUNNING");
        try {
            evalExecutionService.execute(task);
        } catch (Exception e) {
            evalTaskRepository.updateStatus(taskId, "FAILED");
        }
    }

    public List<Map<String, Object>> compareResults(String task1, String task2) {
        return List.of(
                buildCompareMap(task1, computeTaskAverages(task1)),
                buildCompareMap(task2, computeTaskAverages(task2))
        );
    }

    /**
     * 分页查询全部结果并计算平均值，避免硬编码只取前 100 条
     */
    private TaskAverages computeTaskAverages(String taskId) {
        double sumOverall = 0, sumRecall = 0, sumFaith = 0;
        int count = 0;
        int page = 1;
        int pageSize = 500;
        List<EvalResultEntity> batch;

        do {
            batch = evalResultRepository.queryByTaskId(taskId, page, pageSize);
            for (EvalResultEntity r : batch) {
                sumOverall += (r.getOverallScore() != null ? r.getOverallScore() : 0);
                sumRecall += (r.getRecallScore() != null ? r.getRecallScore() : 0);
                sumFaith += (r.getFaithfulnessScore() != null ? r.getFaithfulnessScore() : 0);
                count++;
            }
            page++;
        } while (batch.size() == pageSize); // 批次未满说明已读完

        TaskAverages avg = new TaskAverages();
        avg.avgOverall = count > 0 ? sumOverall / count : 0;
        avg.avgRecall = count > 0 ? sumRecall / count : 0;
        avg.avgFaith = count > 0 ? sumFaith / count : 0;
        avg.count = count;
        return avg;
    }

    private Map<String, Object> buildCompareMap(String taskId, TaskAverages avg) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", taskId);
        map.put("avgOverallScore", avg.avgOverall);
        map.put("avgRecallScore", avg.avgRecall);
        map.put("avgFaithfulnessScore", avg.avgFaith);
        map.put("count", avg.count);
        return map;
    }

    /** 内部聚合结果 */
    private static class TaskAverages {
        double avgOverall;
        double avgRecall;
        double avgFaith;
        int count;
    }
}
