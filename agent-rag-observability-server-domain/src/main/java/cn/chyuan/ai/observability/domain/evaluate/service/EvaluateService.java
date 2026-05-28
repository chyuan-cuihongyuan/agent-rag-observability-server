package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    public void runTask(String taskId) {
        EvalTaskEntity task = evalTaskRepository.queryByTaskId(taskId);
        if (task == null) return;
        evalTaskRepository.updateStatus(taskId, "RUNNING");
    }

    private Map<String, Object> buildCompareMap(String taskId, double avgScore, double avgRecall, double avgFaith, int count) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", taskId);
        map.put("avgOverallScore", avgScore);
        map.put("avgRecallScore", avgRecall);
        map.put("avgFaithfulnessScore", avgFaith);
        map.put("count", count);
        return map;
    }

    public List<Map<String, Object>> compareResults(String task1, String task2) {
        List<EvalResultEntity> results1 = evalResultRepository.queryByTaskId(task1, 1, 100);
        List<EvalResultEntity> results2 = evalResultRepository.queryByTaskId(task2, 1, 100);

        double avgScore1 = results1.stream().mapToDouble(r -> r.getOverallScore() != null ? r.getOverallScore() : 0).average().orElse(0);
        double avgScore2 = results2.stream().mapToDouble(r -> r.getOverallScore() != null ? r.getOverallScore() : 0).average().orElse(0);
        double avgRecall1 = results1.stream().mapToDouble(r -> r.getRecallScore() != null ? r.getRecallScore() : 0).average().orElse(0);
        double avgRecall2 = results2.stream().mapToDouble(r -> r.getRecallScore() != null ? r.getRecallScore() : 0).average().orElse(0);
        double avgFaith1 = results1.stream().mapToDouble(r -> r.getFaithfulnessScore() != null ? r.getFaithfulnessScore() : 0).average().orElse(0);
        double avgFaith2 = results2.stream().mapToDouble(r -> r.getFaithfulnessScore() != null ? r.getFaithfulnessScore() : 0).average().orElse(0);

        List<Map<String, Object>> comparison = new ArrayList<>();
        comparison.add(buildCompareMap(task1, avgScore1, avgRecall1, avgFaith1, results1.size()));
        comparison.add(buildCompareMap(task2, avgScore2, avgRecall2, avgFaith2, results2.size()));
        return comparison;
    }
}
