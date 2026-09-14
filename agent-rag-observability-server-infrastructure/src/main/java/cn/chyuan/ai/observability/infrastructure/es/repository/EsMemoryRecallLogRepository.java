package cn.chyuan.ai.observability.infrastructure.es.repository;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IMemoryRecallLogRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.MemoryRecallLogEntity;
import cn.chyuan.ai.observability.infrastructure.dao.repository.MysqlLogRepository;
import cn.chyuan.ai.observability.infrastructure.es.attributes.OtelAttributeMapper;
import cn.chyuan.ai.observability.infrastructure.es.bulk.EsBulkIndexService;
import cn.chyuan.ai.observability.infrastructure.metrics.ObserveMetrics;
import cn.chyuan.ai.observability.infrastructure.es.support.EsQueryTimer;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆检索追踪 ES 仓储实现
 */
@Slf4j
@Repository
public class EsMemoryRecallLogRepository implements IMemoryRecallLogRepository {

    private static final String INDEX_PREFIX = "memory_recall_log";

    @Resource
    private ElasticsearchClient esClient;

    @Resource
    private EsQueryTimer esQueryTimer;

    @Resource
    private EsBulkIndexService esBulkIndexService;

    @Resource
    private ObserveMetrics observeMetrics;

    @Resource
    private MysqlLogRepository mysqlLogRepository;

    @Override
    public void save(MemoryRecallLogEntity entity) {
        try {
            entity.setOtelAttributes(OtelAttributeMapper.fromMemoryRecall(entity));
            String indexName = INDEX_PREFIX + "-" + entity.getCreateTime().substring(0, 7).replace("-", ".");
            esBulkIndexService.index(indexName, entity.getTraceId(), entity);
        } catch (Exception e) {
            log.error("ES save memory recall log error, traceId={}", entity.getTraceId(), e);
            observeMetrics.recordWriteFailure("es");
        }
        mysqlLogRepository.saveMemoryRecallLog(entity);
    }

    @Override
    public List<MemoryRecallLogEntity> queryByTraceId(String traceId) {
        try {
            SearchResponse<MemoryRecallLogEntity> response = esQueryTimer.timed("memory_recall_by_trace", () -> esClient.search(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(q -> q.term(t -> t.field("traceId").value(traceId)))
                    .size(100),
                    MemoryRecallLogEntity.class));
            return response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES query memory recall log error, traceId={}", traceId, e);
            return Collections.emptyList();
        }
    }
}
