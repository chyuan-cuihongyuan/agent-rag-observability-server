package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;

import java.util.List;

public interface IEvalTaskRepository {
    void save(EvalTaskEntity entity);
    EvalTaskEntity queryByTaskId(String taskId);
    List<EvalTaskEntity> queryList(int page, int size);
    /** 查询最近完成的评测任务（按 update_time 降序），用于全局质量聚合 */
    List<EvalTaskEntity> queryRecentCompleted(int limit);
    void updateStatus(String taskId, String status);
    void updateProgress(String taskId, int completedCount, Double avgScore);
    void updateTotalCount(String taskId, int totalCount);
}
