package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateRecordEntity;

import java.util.List;

/**
 * 门禁记录仓储端口（工单 0136 R4）— 回测判定结论落账与查询。
 */
public interface IGateRecordRepository {

    void insert(GateRecordEntity entity);

    /** 指定门禁的最新一条判定记录（按 create_time/id 降序取一；无记录返回 null） */
    GateRecordEntity queryLatestByGateId(String gateId);

    /** 判定记录历史：gateId 为 null 时查全部（按 create_time 降序分页） */
    List<GateRecordEntity> queryList(String gateId, int page, int size);

    /** 按评测任务查判定记录（一任务一条） */
    GateRecordEntity queryByTaskId(String taskId);
}
