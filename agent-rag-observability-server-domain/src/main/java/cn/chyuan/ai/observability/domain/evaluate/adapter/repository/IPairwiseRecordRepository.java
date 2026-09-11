package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.PairwiseRecordEntity;

import java.util.List;

/**
 * pairwise 对局记录仓储端口（工单 0170 X1）— X2 Elo 重算的数据源。
 */
public interface IPairwiseRecordRepository {

    void insert(String taskA, String taskB, String datasetId, String query, String outcome, int pairNo);

    /** 对局记录（taskA/taskB 可空过滤，按 id 升序）；limit 钳制 ≤5000 */
    List<PairwiseRecordEntity> queryList(String taskA, String taskB, int limit);
}
