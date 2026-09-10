package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;

import java.util.List;

public interface IEvalResultRepository {
    void save(EvalResultEntity entity);
    void batchSave(List<EvalResultEntity> entities);
    /** trial 为 null 时查全部 trial（兼容既有调用方），非空时按 trial_no 过滤（工单 0135 R3） */
    List<EvalResultEntity> queryByTaskId(String taskId, Integer trial, int page, int size);
    long countByTaskId(String taskId);
}
