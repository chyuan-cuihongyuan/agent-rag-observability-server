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

    /**
     * 按终态状态集合查最近链路（工单 0138 S2：Case 挖掘来源②——失败/超时链路）。
     *
     * @param statuses 终态集合（如 FAIL/TIMEOUT），空集合返回空列表
     * @param limit    返回条数上限（按 createTime 降序）
     */
    List<ChatResultEntity> queryByStatuses(List<String> statuses, int limit);

    /**
     * 成本聚合取数（工单 0148 U2）— 时间窗内的链路摘要（createTime/agentId/modelVersion/tokens）。
     * 读时派生口径：聚合服务按计价表即时算成本，不依赖落库 cost 列。
     *
     * @param startTime 起始时间（含），格式 yyyy-MM-dd HH:mm:ss
     * @param limit     上限（钳制 ≤5000）
     */
    List<ChatResultEntity> queryCostSources(String startTime, int limit);
}
