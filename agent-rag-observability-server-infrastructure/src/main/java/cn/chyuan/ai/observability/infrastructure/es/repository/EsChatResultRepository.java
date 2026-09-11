package cn.chyuan.ai.observability.infrastructure.es.repository;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import cn.chyuan.ai.observability.infrastructure.dao.repository.MysqlLogRepository;
import cn.chyuan.ai.observability.infrastructure.es.bulk.EsBulkIndexService;
import cn.chyuan.ai.observability.infrastructure.metrics.ObserveMetrics;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import java.util.*;

@Slf4j
@Repository
public class EsChatResultRepository implements IChatResultRepository {

    private static final String INDEX_PREFIX = "chat_result_log";

    @Resource
    private ElasticsearchClient esClient;

    @Resource
    private MysqlLogRepository mysqlLogRepository;

    @Resource
    private EsBulkIndexService esBulkIndexService;

    @Resource
    private ObserveMetrics observeMetrics;

    @Override
    public void save(ChatResultEntity entity) {
        try {
            String indexName = INDEX_PREFIX + "-" + entity.getCreateTime().substring(0, 7).replace("-", ".");
            esBulkIndexService.index(indexName, entity.getTraceId(), entity);
        } catch (Exception e) {
            log.error("ES save chat result error, traceId={}", entity.getTraceId(), e);
            observeMetrics.recordWriteFailure("es");
        }
        mysqlLogRepository.saveChatResultLog(entity);
        observeMetrics.recordChatResult(entity.getSourceService(), entity.getFinalStatus(),
                entity.getPromptTokens(), entity.getCompletionTokens(), entity.getTotalCostTimeMs());
    }

    @Override
    public ChatResultEntity queryByTraceId(String traceId) {
        try {
            SearchResponse<ChatResultEntity> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(q -> q.term(t -> t.field("traceId").value(traceId)))
                    .size(1), ChatResultEntity.class);
            return response.hits().hits().isEmpty() ? null : response.hits().hits().get(0).source();
        } catch (Exception e) {
            log.error("ES query chat result error, traceId={}", traceId, e);
            return null;
        }
    }

    @Override
    public List<ChatResultEntity> queryByQuestion(String queryText, int limit) {
        try {
            SearchResponse<ChatResultEntity> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(q -> q.match(m -> m.field("question").query(queryText)))
                    .sort(sort -> sort.field(f -> f.field("createTime").order(SortOrder.Desc)))
                    .size(limit), ChatResultEntity.class);
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
        } catch (Exception e) {
            log.error("ES query by question error, queryText={}", queryText, e);
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> statTrend(String startTime, String endTime, String interval) {
        try {
            SearchResponse<Void> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .size(0)
                    .query(q -> q.range(r -> r.field("createTime").gte(co.elastic.clients.json.JsonData.of(startTime)).lte(co.elastic.clients.json.JsonData.of(endTime))))
                    .aggregations("trend", a -> a
                            .dateHistogram(dh -> dh.field("createTime")
                                    .fixedInterval(Time.of(t -> t.time("hour".equals(interval) ? "1h" : "1d"))))
                            // 子聚合：平均耗时
                            .aggregations("avg_cost", sa -> sa.avg(av -> av.field("totalCostTimeMs")))
                            // 子聚合：失败数量
                            .aggregations("fail_filter", sa -> sa.filter(f -> f.term(t -> t.field("finalStatus").value("FAIL"))))),
                    Void.class);
            List<Map<String, Object>> result = new ArrayList<>();
            response.aggregations().get("trend").dateHistogram().buckets().array().forEach(b -> {
                // 提取子聚合结果
                double avgCost = 0;
                long failCount = 0;
                try {
                    var avgAgg = b.aggregations().get("avg_cost");
                    if (avgAgg != null && avgAgg.avg() != null) {
                        avgCost = avgAgg.avg().value();
                        if (Double.isNaN(avgCost) || Double.isInfinite(avgCost)) avgCost = 0;
                    }
                } catch (Exception e) {
                    log.debug("提取 avg_cost 子聚合失败: {}", e.getMessage());
                }
                try {
                    var failAgg = b.aggregations().get("fail_filter");
                    if (failAgg != null && failAgg.filter() != null) {
                        failCount = failAgg.filter().docCount();
                    }
                } catch (Exception e) {
                    log.debug("提取 fail_filter 子聚合失败: {}", e.getMessage());
                }

                Map<String, Object> item = new HashMap<>();
                item.put("time_bucket", b.keyAsString());
                item.put("request_count", b.docCount());
                item.put("avg_cost_ms", Math.round(avgCost * 100.0) / 100.0);
                item.put("fail_count", failCount);
                result.add(item);
            });
            return result;
        } catch (Exception e) {
            log.error("ES stat trend error", e);
            return Collections.emptyList();
        }
    }

    @Override
    public double avgCostTime(String startTime, String endTime) {
        try {
            SearchResponse<Void> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .size(0)
                    .query(q -> q.range(r -> r.field("createTime").gte(co.elastic.clients.json.JsonData.of(startTime)).lte(co.elastic.clients.json.JsonData.of(endTime))))
                    .aggregations("avg_cost", a -> a.avg(av -> av.field("totalCostTimeMs"))),
                    Void.class);
            return response.aggregations().get("avg_cost").avg().value();
        } catch (Exception e) {
            log.error("ES avg cost time error", e);
            return 0;
        }
    }

    @Override
    public long countByStatus(String status, String startTime, String endTime) {
        try {
            SearchResponse<Void> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .size(0)
                    .query(q -> q.bool(b -> b.must(m -> m.range(r -> r.field("createTime").gte(co.elastic.clients.json.JsonData.of(startTime)).lte(co.elastic.clients.json.JsonData.of(endTime)))).must(m -> m.term(t -> t.field("finalStatus").value(status))))),
                    Void.class);
            return response.hits().total().value();
        } catch (Exception e) {
            log.error("ES count by status error", e);
            return 0;
        }
    }

    /**
     * 按终态状态集合查最近链路（工单 0138 S2：Case 挖掘来源②）。
     * terms 查询多状态 + createTime 降序；ES 故障返回空列表（挖掘来源隔离由调用方兜底）。
     */
    @Override
    public List<ChatResultEntity> queryByStatuses(List<String> statuses, int limit) {
        if (statuses == null || statuses.isEmpty() || limit <= 0) {
            return List.of();
        }
        try {
            List<co.elastic.clients.elasticsearch._types.FieldValue> values = statuses.stream()
                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                    .toList();
            SearchResponse<ChatResultEntity> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(q -> q.terms(t -> t.field("finalStatus").terms(tv -> tv.value(values))))
                    .sort(sort -> sort.field(f -> f.field("createTime").order(SortOrder.Desc)))
                    .size(Math.min(limit, 200)), ChatResultEntity.class);
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
        } catch (Exception e) {
            log.error("ES query by statuses error, statuses={}", statuses, e);
            return List.of();
        }
    }

    /** 成本聚合取数（工单 0148 U2）：时间窗 + 摘要字段 source 过滤，按 createTime 降序。 */
    @Override
    public List<ChatResultEntity> queryCostSources(String startTime, int limit) {
        try {
            SearchResponse<ChatResultEntity> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(q -> q.range(r -> r.field("createTime")
                            .gte(co.elastic.clients.json.JsonData.of(startTime))))
                    .source(src -> src.filter(f -> f.includes(
                            "createTime", "agentId", "modelVersion", "promptTokens", "completionTokens", "finalStatus")))
                    .sort(sort -> sort.field(f -> f.field("createTime").order(SortOrder.Desc)))
                    .size(Math.min(Math.max(limit, 1), 5000)), ChatResultEntity.class);
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
        } catch (Exception e) {
            log.error("ES query cost sources error, startTime={}", startTime, e);
            return List.of();
        }
    }
}
