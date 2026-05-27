package cn.chyuan.ai.observability.domain.observe.adapter.repository;

import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;

import java.util.List;
import java.util.Map;

public interface IChatResultRepository {
    void save(ChatResultEntity entity);
    ChatResultEntity queryByTraceId(String traceId);
    List<Map<String, Object>> statTrend(String startTime, String endTime, String interval);
    double avgCostTime(String startTime, String endTime);
    long countByStatus(String status, String startTime, String endTime);
}
