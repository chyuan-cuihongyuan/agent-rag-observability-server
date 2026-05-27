package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;

import java.util.List;

public interface IEvalResultRepository {
    void save(EvalResultEntity entity);
    void batchSave(List<EvalResultEntity> entities);
    List<EvalResultEntity> queryByTaskId(String taskId, int page, int size);
    long countByTaskId(String taskId);
}
