package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;

import java.util.List;

public interface IEvalDatasetRepository {
    void save(EvalDatasetEntity entity);

    EvalDatasetEntity queryByDatasetId(String datasetId);

    List<EvalDatasetEntity> queryList(int page, int size);

    // ========== 新增：版本化 + 三池 + 冻结（工单 0134 R2） ==========

    /** 更新可变字段（description/itemCount/itemsJson/pool/source），datasetName/version 不动 */
    boolean update(EvalDatasetEntity entity);

    /**
     * 按样本池筛选：pool ∈ {golden, challenge, wrong}；pool 传 null 表示未分类
     * （存量数据 pool 为 NULL 的查询兼容口径）。
     */
    List<EvalDatasetEntity> queryByPool(String pool, int page, int size);

    /** 同名数据集的全部版本（版本号降序） */
    List<EvalDatasetEntity> queryVersions(String datasetName);

    /** 同名数据集的最大版本号（无同名时返回 0） */
    int maxVersion(String datasetName);

    /** 冻结/解冻指定数据集版本 */
    boolean updateFrozen(String datasetId, boolean frozen);
}
