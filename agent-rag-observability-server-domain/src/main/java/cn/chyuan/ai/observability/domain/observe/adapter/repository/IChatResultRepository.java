package cn.chyuan.ai.observability.domain.observe.adapter.repository;

import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;

import java.util.List;
import java.util.Map;

public interface IChatResultRepository {
    void save(ChatResultEntity entity);
    ChatResultEntity queryByTraceId(String traceId);
    /**
     * 按查询文本匹配查找最近的聊天记录（用于离线评测复用）
     *
     * @param queryText 查询文本
     * @param limit     返回记录数量限制
     * @return 匹配的聊天记录列表，按时间倒序
     */
    List<ChatResultEntity> queryByQuestion(String queryText, int limit);
    List<Map<String, Object>> statTrend(String startTime, String endTime, String interval);
    double avgCostTime(String startTime, String endTime);
    long countByStatus(String status, String startTime, String endTime);
}
