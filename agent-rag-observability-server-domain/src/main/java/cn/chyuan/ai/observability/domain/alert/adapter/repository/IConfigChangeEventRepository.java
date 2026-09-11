package cn.chyuan.ai.observability.domain.alert.adapter.repository;

import cn.chyuan.ai.observability.domain.alert.model.entity.ConfigChangeEventEntity;

import java.util.List;

/**
 * 配置变更事件仓储端口（工单 0182 Y6）。
 */
public interface IConfigChangeEventRepository {

    void insert(ConfigChangeEventEntity entity);

    /** 事件列表：tableName/operator 传 null 不过滤（按 create_time 降序分页） */
    List<ConfigChangeEventEntity> queryList(String tableName, String operator, int page, int size);
}
