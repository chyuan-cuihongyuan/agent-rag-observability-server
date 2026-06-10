package cn.chyuan.ai.observability.infrastructure.es.repository;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IToolCallLogRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.ToolCallLogEntity;
import cn.chyuan.ai.observability.infrastructure.dao.repository.MysqlLogRepository;
import cn.chyuan.ai.observability.infrastructure.es.bulk.EsBulkIndexService;
import cn.chyuan.ai.observability.infrastructure.metrics.ObserveMetrics;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工具调用追踪 ES 仓储实现
 */
@Slf4j
@Repository
public class EsToolCallLogRepository implements IToolCallLogRepository {

    private static final String INDEX_PREFIX = "tool_call_log";

    @Resource
    private ElasticsearchClient esClient;

    @Resource
    private EsBulkIndexService esBulkIndexService;

    @Resource
    private ObserveMetrics observeMetrics;

    @Resource
    private MysqlLogRepository mysqlLogRepository;

    @Override
    public void save(ToolCallLogEntity entity) {
        try {
            String indexName = INDEX_PREFIX + "-" + entity.getCreateTime().substring(0, 7).replace("-", ".");
            // 文档 ID：traceId + spanId，spanId 为空时使用序号避免覆盖
            String docId = entity.getTraceId() + "_" + (entity.getSpanId() != null ? entity.getSpanId() : UUID.randomUUID());
            esBulkIndexService.index(indexName, docId, entity);
        } catch (Exception e) {
            log.error("ES save tool call log error, traceId={}", entity.getTraceId(), e);
            observeMetrics.recordWriteFailure("es");
        }
        mysqlLogRepository.saveToolCallLog(entity);
    }

    @Override
    public List<ToolCallLogEntity> queryByTraceId(String traceId) {
        try {
            SearchResponse<ToolCallLogEntity> response = esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(q -> q.term(t -> t.field("traceId").value(traceId)))
                    .size(100)
                    .sort(so -> so.field(f -> f.field("callOrder").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc))),
                    ToolCallLogEntity.class);
            return response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES query tool call log error, traceId={}", traceId, e);
            return Collections.emptyList();
        }
    }
}
