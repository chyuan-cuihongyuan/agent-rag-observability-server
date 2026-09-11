package cn.chyuan.ai.observability.domain.patrol.adapter.repository;

import cn.chyuan.ai.observability.domain.patrol.model.entity.PatrolRecordEntity;

import java.util.List;

/**
 * 巡检拨测记录仓储端口（工单 0137 S1）。
 */
public interface IPatrolRecordRepository {

    void insert(PatrolRecordEntity entity);

    /** 拨测记录历史（按 create_time/id 降序分页） */
    List<PatrolRecordEntity> queryList(int page, int size);

    /** 最近一条拨测记录（无记录返回 null） */
    PatrolRecordEntity queryLatest();

    /** 指定轮次的全部记录（按 id 升序，重放本轮执行顺序） */
    List<PatrolRecordEntity> queryByRoundId(String roundId);
}
