package cn.chyuan.ai.observability.domain.alert.adapter.repository;

import cn.chyuan.ai.observability.domain.alert.model.entity.AlertSilenceEntity;

import java.util.List;

/**
 * 告警静默仓储端口（工单 0179 Y3）。
 */
public interface IAlertSilenceRepository {

    void insert(AlertSilenceEntity entity);

    /** 全量静默规则（量小，不分页；过期规则评估时惰性跳过） */
    List<AlertSilenceEntity> queryAll();

    boolean deleteById(long id);
}
