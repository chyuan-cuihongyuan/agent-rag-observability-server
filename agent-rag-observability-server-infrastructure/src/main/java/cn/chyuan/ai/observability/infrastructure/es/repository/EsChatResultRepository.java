package cn.chyuan.ai.observability.infrastructure.es.repository;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import cn.chyuan.ai.observability.infrastructure.dao.repository.MysqlLogRepository;
import cn.chyuan.ai.observability.infrastructure.es.bulk.EsBulkIndexService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

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

    @Override
    public void save(ChatResultEntity entity) {
        try {
            String indexName = INDEX_PREFIX + "-" + entity.getCreateTime().substring(0, 7).replace("-", ".");
            esBulkIndexService.index(indexName, entity.getTraceId(), entity);
        } catch (Exception e) {
            log.error("ES save chat result error, traceId={}", entity.getTraceId(), e);
        }
        mysqlLogRepository.saveChatResultLog(entity);
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
    public List<Map<String, Object>> statTrend(String startTime, String endTime, String interval) {
        try {
            SearchResponse<Void> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .size(0)
                    .query(q -> q.range(r -> r.field("createTime").gte(co.elastic.clients.json.JsonData.of(startTime)).lte(co.elastic.clients.json.JsonData.of(endTime))))
                    .aggregations("trend", a -> a.dateHistogram(dh -> dh.field("createTime")
                            .fixedInterval(Time.of(t -> t.time("hour".equals(interval) ? "1h" : "1d"))))),
                    Void.class);
            List<Map<String, Object>> result = new ArrayList<>();
            response.aggregations().get("trend").dateHistogram().buckets().array().forEach(b ->
                    result.add(Map.of("time", b.keyAsString(), "count", b.docCount())));
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
}
