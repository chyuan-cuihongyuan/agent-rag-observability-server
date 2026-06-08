package cn.chyuan.ai.observability.domain.observe.adapter.repository;

import cn.chyuan.ai.observability.domain.observe.model.entity.MemoryRecallLogEntity;

import java.util.List;

/**
 * 记忆检索追踪仓储接口
 */
public interface IMemoryRecallLogRepository {
    void save(MemoryRecallLogEntity entity);
    List<MemoryRecallLogEntity> queryByTraceId(String traceId);
}
