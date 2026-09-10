package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;

import java.util.List;

/**
 * 门禁规则仓储端口（工单 0136 R4）。
 */
public interface IGateRepository {

    void insert(GateEntity entity);

    GateEntity queryByGateId(String gateId);

    GateEntity queryByName(String name);

    List<GateEntity> queryList(int page, int size);

    /** 按 gateId 更新可变字段（name/safetyDims/scoreThresholds/trials/enabled） */
    boolean update(GateEntity entity);
}
