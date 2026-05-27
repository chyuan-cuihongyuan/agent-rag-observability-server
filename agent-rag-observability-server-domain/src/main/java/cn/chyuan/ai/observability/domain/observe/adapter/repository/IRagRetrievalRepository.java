package cn.chyuan.ai.observability.domain.observe.adapter.repository;

import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;

import java.util.List;
import java.util.Map;

public interface IRagRetrievalRepository {
    void save(RagRetrievalEntity entity);
    RagRetrievalEntity queryByTraceId(String traceId);
    List<Map<String, Object>> statEmptyRetrievalRate(String startTime, String endTime);
    double avgRetrievalCount(String startTime, String endTime);
}
