package cn.chyuan.ai.observability.domain.observe.adapter.repository;

import cn.chyuan.ai.observability.domain.observe.model.entity.ToolCallLogEntity;

import java.util.List;

/**
 * 工具调用追踪仓储接口
 */
public interface IToolCallLogRepository {
    void save(ToolCallLogEntity entity);
    List<ToolCallLogEntity> queryByTraceId(String traceId);
}
