package cn.chyuan.ai.observability.domain.observe.adapter.repository;

import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;

import java.util.List;
import java.util.Map;

public interface IAgentDecisionRepository {
    void save(AgentDecisionEntity entity);
    AgentDecisionEntity queryByTraceId(String traceId);
    List<AgentDecisionEntity> queryBySessionId(String sessionId, int page, int size);
    List<AgentDecisionEntity> queryByUserId(String tenantId, String ownerUserId, int page, int size);
    List<AgentDecisionEntity> queryByCondition(Map<String, Object> condition, int page, int size);
    long countByCondition(Map<String, Object> condition);
    List<Map<String, Object>> statByBranchType(String startTime, String endTime);
    List<Map<String, Object>> statByToolUsage(String startTime, String endTime);
    List<Map<String, Object>> statErrorRanking(String startTime, String endTime);
}
