package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

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
}
