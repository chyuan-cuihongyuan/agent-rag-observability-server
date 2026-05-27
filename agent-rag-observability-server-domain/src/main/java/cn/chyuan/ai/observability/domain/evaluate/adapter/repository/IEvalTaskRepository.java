package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;

import java.util.List;

public interface IEvalTaskRepository {
    void save(EvalTaskEntity entity);
    EvalTaskEntity queryByTaskId(String taskId);
    List<EvalTaskEntity> queryList(int page, int size);
    void updateStatus(String taskId, String status);
    void updateProgress(String taskId, int completedCount, Double avgScore);
}
