package cn.chyuan.ai.observability.infrastructure.es.repository;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.infrastructure.es.support.EsQueryTimer;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import cn.chyuan.ai.observability.infrastructure.dao.repository.MysqlLogRepository;
import cn.chyuan.ai.observability.infrastructure.es.attributes.OtelAttributeMapper;
import cn.chyuan.ai.observability.infrastructure.es.bulk.EsBulkIndexService;
import cn.chyuan.ai.observability.infrastructure.metrics.ObserveMetrics;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;

@Slf4j
@Repository
public class EsRagRetrievalRepository implements IRagRetrievalRepository {

    private static final String INDEX_PREFIX = "rag_retrieval_log";

    @Resource
    private ElasticsearchClient esClient;

    @Resource
    private EsQueryTimer esQueryTimer;

    @Resource
    private MysqlLogRepository mysqlLogRepository;

    @Resource
    private EsBulkIndexService esBulkIndexService;

    @Resource
    private ObserveMetrics observeMetrics;

    @Override
    public void save(RagRetrievalEntity entity) {
        try {
            entity.setOtelAttributes(OtelAttributeMapper.fromRagRetrieval(entity));
            String indexName = INDEX_PREFIX + "-" + entity.getCreateTime().substring(0, 7).replace("-", ".");
            esBulkIndexService.index(indexName, entity.getTraceId(), entity);
        } catch (Exception e) {
            log.error("ES save rag retrieval error, traceId={}", entity.getTraceId(), e);
            observeMetrics.recordWriteFailure("es");
        }
        mysqlLogRepository.saveRetrievalLog(entity);
        if (entity.getEmptyRetrieval() != null && entity.getEmptyRetrieval() == 1) {
            observeMetrics.recordEmptyRetrieval();
        }
        if (entity.getRetrievalCostMs() != null) {
            observeMetrics.recordRagRetrievalDuration(entity.getRetrievalCostMs());
        }
    }

    @Override
    public RagRetrievalEntity queryByTraceId(String traceId) {
        try {
            SearchResponse<RagRetrievalEntity> response = esQueryTimer.timed("rag_retrieval_by_trace", () -> esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(q -> q.term(t -> t.field("traceId").value(traceId)))
                    .size(1), RagRetrievalEntity.class));
            return response.hits().hits().isEmpty() ? null : response.hits().hits().get(0).source();
        } catch (Exception e) {
            log.error("ES query rag retrieval error, traceId={}", traceId, e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> statEmptyRetrievalRate(String startTime, String endTime) {
        try {
            SearchResponse<Void> response = esQueryTimer.timed("rag_retrieval_statEmptyRetrievalRate", () -> esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .size(0)
                    .query(q -> q.range(r -> r.field("createTime").gte(co.elastic.clients.json.JsonData.of(startTime)).lte(co.elastic.clients.json.JsonData.of(endTime))))
                    .aggregations("by_empty", a -> a.terms(t -> t.field("emptyRetrieval").size(2))),
                    Void.class));
            List<Map<String, Object>> result = new ArrayList<>();
            // emptyRetrieval 字段为 integer 类型，使用 lterms() 获取 LongTerms 聚合结果
            response.aggregations().get("by_empty").lterms().buckets().array().forEach(b ->
                    result.add(Map.of("emptyRetrieval", String.valueOf(b.key()), "count", b.docCount())));
            return result;
        } catch (Exception e) {
            log.error("ES stat empty retrieval error", e);
            return Collections.emptyList();
        }
    }

    @Override
    public double avgRetrievalCount(String startTime, String endTime) {
        try {
            SearchResponse<Void> response = esQueryTimer.timed("rag_retrieval_avgRetrievalCount", () -> esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .size(0)
                    .query(q -> q.range(r -> r.field("createTime").gte(co.elastic.clients.json.JsonData.of(startTime)).lte(co.elastic.clients.json.JsonData.of(endTime))))
                    .aggregations("avg_count", a -> a.avg(av -> av.field("retrievalCount"))),
                    Void.class));
            return response.aggregations().get("avg_count").avg().value();
        } catch (Exception e) {
            log.error("ES avg retrieval count error", e);
            return 0;
        }
    }
}
