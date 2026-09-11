package cn.chyuan.ai.observability.domain.insight.adapter.repository;

import cn.chyuan.ai.observability.domain.insight.model.entity.TraceAnnotationEntity;

import java.util.List;

/**
 * 人工注解仓储端口（工单 0150 U4）— 唯一键 (trace_id, operator)，保存为 upsert 语义。
 */
public interface ITraceAnnotationRepository {

    /** 保存（同 trace+operator 覆盖更新，重评即改判） */
    void upsert(TraceAnnotationEntity entity);

    TraceAnnotationEntity queryByTraceAndOperator(String traceId, String operator);

    /** 注解列表：score 传 null 不过滤（按 update_time 降序分页） */
    List<TraceAnnotationEntity> queryList(Integer score, int page, int size);
}
