package cn.chyuan.ai.observability.infrastructure.es.repository;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IAgentDecisionRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import cn.chyuan.ai.observability.infrastructure.dao.repository.MysqlLogRepository;
import cn.chyuan.ai.observability.infrastructure.es.bulk.EsBulkIndexService;
import cn.chyuan.ai.observability.infrastructure.metrics.ObserveMetrics;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class EsAgentDecisionRepository implements IAgentDecisionRepository {

    private static final String INDEX_PREFIX = "agent_decision_log";

    @Resource
    private ElasticsearchClient esClient;

    @Resource
    private MysqlLogRepository mysqlLogRepository;

    @Resource
    private EsBulkIndexService esBulkIndexService;

    @Resource
    private ObserveMetrics observeMetrics;

    @Override
    public void save(AgentDecisionEntity entity) {
        try {
            String indexName = INDEX_PREFIX + "-" + entity.getCreateTime().substring(0, 7).replace("-", ".");
            esBulkIndexService.index(indexName, entity.getTraceId(), entity);
        } catch (Exception e) {
            log.error("ES save agent decision error, traceId={}", entity.getTraceId(), e);
            observeMetrics.recordWriteFailure("es");
        }
        mysqlLogRepository.saveDecisionLog(entity);
        observeMetrics.recordRequest(entity.getSourceService(), entity.getAgentId(), entity.getBranchType());
        observeMetrics.recordAgentStatus(entity.getAgentStatus());
        if (entity.getCostTimeMs() != null) {
            observeMetrics.recordRequestDuration(entity.getCostTimeMs());
        }
    }

    @Override
    public AgentDecisionEntity queryByTraceId(String traceId) {
        try {
            SearchResponse<AgentDecisionEntity> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(q -> q.term(t -> t.field("traceId").value(traceId)))
                    .size(1), AgentDecisionEntity.class);
            return response.hits().hits().isEmpty() ? null : response.hits().hits().get(0).source();
        } catch (Exception e) {
            log.error("ES query agent decision error, traceId={}", traceId, e);
            return null;
        }
    }

    @Override
    public List<AgentDecisionEntity> queryBySessionId(String sessionId, int page, int size) {
        return queryByCondition(Map.of("sessionId", sessionId), page, size);
    }

    @Override
    public List<AgentDecisionEntity> queryByUserId(String tenantId, String ownerUserId, int page, int size) {
        Map<String, Object> cond = new HashMap<>();
        cond.put("tenantId", tenantId);
        cond.put("ownerUserId", ownerUserId);
        return queryByCondition(cond, page, size);
    }

    @Override
    public List<AgentDecisionEntity> queryByCondition(Map<String, Object> condition, int page, int size) {
        try {
            List<Query> must = new ArrayList<>();
            if (condition.containsKey("traceId")) must.add(Query.of(q -> q.term(t -> t.field("traceId").value(condition.get("traceId").toString()))));
            if (condition.containsKey("sessionId")) must.add(Query.of(q -> q.term(t -> t.field("sessionId").value(condition.get("sessionId").toString()))));
            if (condition.containsKey("tenantId")) must.add(Query.of(q -> q.term(t -> t.field("tenantId").value(condition.get("tenantId").toString()))));
            if (condition.containsKey("ownerUserId")) must.add(Query.of(q -> q.term(t -> t.field("ownerUserId").value(condition.get("ownerUserId").toString()))));
            if (condition.containsKey("agentId")) must.add(Query.of(q -> q.term(t -> t.field("agentId").value(condition.get("agentId").toString()))));
            if (condition.containsKey("branchType")) must.add(Query.of(q -> q.term(t -> t.field("branchType").value(condition.get("branchType").toString()))));
            if (condition.containsKey("agentStatus")) must.add(Query.of(q -> q.term(t -> t.field("agentStatus").value(condition.get("agentStatus").toString()))));
            if (condition.containsKey("sourceService")) must.add(Query.of(q -> q.term(t -> t.field("sourceService").value(condition.get("sourceService").toString()))));

            final List<Query> mustQueries = must;
            SearchResponse<AgentDecisionEntity> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(q -> q.bool(b -> b.must(mustQueries)))
                    .from((page - 1) * size)
                    .size(size)
                    .sort(so -> so.field(f -> f.field("createTime").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc))),
                    AgentDecisionEntity.class);
            return response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES query agent decision by condition error", e);
            return Collections.emptyList();
        }
    }

    @Override
    public long countByCondition(Map<String, Object> condition) {
        try {
            List<Query> must = new ArrayList<>();
            for (Map.Entry<String, Object> entry : condition.entrySet()) {
                String key = entry.getKey();
                if (key.equals("startTime") || key.equals("endTime")) continue;
                must.add(Query.of(q -> q.term(t -> t.field(key).value(entry.getValue().toString()))));
            }
            if (condition.containsKey("startTime") || condition.containsKey("endTime")) {
                must.add(Query.of(q -> q.range(r -> {
                    r.field("createTime");
                    if (condition.containsKey("startTime")) r.gte(co.elastic.clients.json.JsonData.of(condition.get("startTime").toString()));
                    if (condition.containsKey("endTime")) r.lte(co.elastic.clients.json.JsonData.of(condition.get("endTime").toString()));
                    return r;
                })));
            }
            final List<Query> mustQueries = must;
            SearchResponse<Void> response = esClient.search(s -> s.index(INDEX_PREFIX + "*").query(q -> q.bool(b -> b.must(mustQueries))).size(0), Void.class);
            return response.hits().total().value();
        } catch (Exception e) {
            log.error("ES count agent decision error", e);
            return 0;
        }
    }

    @Override
    public List<Map<String, Object>> statByBranchType(String startTime, String endTime) {
        try {
            SearchResponse<Void> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .size(0)
                    .query(q -> q.range(r -> r.field("createTime").gte(co.elastic.clients.json.JsonData.of(startTime)).lte(co.elastic.clients.json.JsonData.of(endTime))))
                    .aggregations("by_branch", a -> a.terms(t -> t.field("branchType").size(10))),
                    Void.class);
            List<Map<String, Object>> result = new ArrayList<>();
            response.aggregations().get("by_branch").sterms().buckets().array().forEach(b ->
                    result.add(Map.of("branch_type", b.key().stringValue(), "count", b.docCount())));
            return result;
        } catch (Exception e) {
            log.error("ES stat branch type error", e);
            return Collections.emptyList();
        }
    }

    /** 应用层聚合最大查询文档数，避免硬编码截断 */
    private static final int MAX_AGGREGATION_SIZE = 10000;

    @Override
    public List<Map<String, Object>> statByToolUsage(String startTime, String endTime) {
        try {
            // selectedToolList 是 object 类型，无法直接 ES terms 聚合
            // 从 ES 查询记录的 selectedToolList 字段，应用层解析计数
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .size(MAX_AGGREGATION_SIZE)
                    .source(src -> src.filter(f -> f.includes("selectedToolList")))
                    .query(q -> q.range(r -> r.field("createTime")
                            .gte(co.elastic.clients.json.JsonData.of(startTime))
                            .lte(co.elastic.clients.json.JsonData.of(endTime)))),
                    Map.class);

            // 应用层解析 selectedToolList JSON 字符串，计数工具使用频率
            Map<String, Long> toolCountMap = new HashMap<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                if (source == null) continue;
                Object toolListObj = source.get("selectedToolList");
                if (toolListObj == null) continue;

                // selectedToolList 可能是 JSON 字符串或已解析的对象
                if (toolListObj instanceof String toolListStr && !toolListStr.isBlank()) {
                    try {
                        JSONArray tools = JSON.parseArray(toolListStr);
                        for (int i = 0; i < tools.size(); i++) {
                            String toolName = tools.getString(i);
                            if (toolName != null && !toolName.isBlank()) {
                                toolCountMap.merge(toolName, 1L, Long::sum);
                            }
                        }
                    } catch (Exception e) {
                        // 非 JSON 数组格式，记录后跳过
                        log.debug("selectedToolList JSON 解析失败: {}", e.getMessage());
                    }
                } else if (toolListObj instanceof List<?> tools) {
                    for (Object tool : tools) {
                        if (tool != null) {
                            toolCountMap.merge(tool.toString(), 1L, Long::sum);
                        }
                    }
                }
            }

            // 按计数降序排列
            List<Map<String, Object>> result = new ArrayList<>();
            toolCountMap.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> result.add(Map.of(
                            "tool_name", entry.getKey(),
                            "call_count", entry.getValue(),
                            "avg_cost_ms", 0)));

            return result;
        } catch (Exception e) {
            log.error("ES stat tool usage error", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> statErrorRanking(String startTime, String endTime) {
        try {
            // 查询失败状态的记录，获取错误消息和 Agent ID
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .size(MAX_AGGREGATION_SIZE)
                    .source(src -> src.filter(f -> f.includes("errorMessage", "agentId")))
                    .query(q -> q.bool(b -> b
                            .must(m -> m.range(r -> r.field("createTime")
                                    .gte(co.elastic.clients.json.JsonData.of(startTime))
                                    .lte(co.elastic.clients.json.JsonData.of(endTime))))
                            .must(m -> m.term(t -> t.field("agentStatus").value("FAIL")))))
                    .sort(so -> so.field(f -> f.field("createTime").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc))),
                    Map.class);

            // 应用层按错误消息分组计数
            Map<String, Map<String, Object>> errorMap = new LinkedHashMap<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                if (source == null) continue;
                String errorMsg = source.get("errorMessage") != null ? source.get("errorMessage").toString() : "未知错误";
                String agentId = source.get("agentId") != null ? source.get("agentId").toString() : "-";

                if (!errorMap.containsKey(errorMsg)) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("error_message", errorMsg);
                    item.put("agent_id", agentId);
                    item.put("count", 1L);
                    errorMap.put(errorMsg, item);
                } else {
                    Map<String, Object> item = errorMap.get(errorMsg);
                    item.put("count", (Long) item.get("count") + 1);
                }
            }

            // 按计数降序排列，取前 10
            return errorMap.values().stream()
                    .sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                    .limit(10)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES stat error ranking error", e);
            return Collections.emptyList();
        }
    }
}
