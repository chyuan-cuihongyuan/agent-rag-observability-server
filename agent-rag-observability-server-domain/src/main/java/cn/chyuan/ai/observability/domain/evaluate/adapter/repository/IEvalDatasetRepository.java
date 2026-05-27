package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;

import java.util.List;

public interface IEvalDatasetRepository {
    void save(EvalDatasetEntity entity);
    EvalDatasetEntity queryByDatasetId(String datasetId);
    List<EvalDatasetEntity> queryList(int page, int size);
}
