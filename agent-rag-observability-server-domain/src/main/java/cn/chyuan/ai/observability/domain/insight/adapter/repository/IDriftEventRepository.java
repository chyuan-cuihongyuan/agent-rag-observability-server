package cn.chyuan.ai.observability.domain.insight.adapter.repository;

import cn.chyuan.ai.observability.domain.insight.model.entity.DriftEventEntity;

import java.util.List;

/**
 * 漂移事件仓储端口（工单 0154 U8）。
 */
public interface IDriftEventRepository {

    void save(DriftEventEntity entity);

    /** 事件历史（按 create_time 降序分页） */
    List<DriftEventEntity> queryList(int page, int size);
}
