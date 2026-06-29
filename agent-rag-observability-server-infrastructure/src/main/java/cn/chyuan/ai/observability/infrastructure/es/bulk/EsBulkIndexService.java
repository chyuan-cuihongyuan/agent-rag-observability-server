package cn.chyuan.ai.observability.infrastructure.es.bulk;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EsBulkIndexService {

    @Resource
    private ElasticsearchClient esClient;

    @Resource
    private EsBulkProperties properties;

    private BlockingQueue<IndexCommand> queue;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("ES bulk index disabled, repositories will write synchronously");
            return;
        }
        queue = new ArrayBlockingQueue<>(properties.getQueueCapacity());
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "observe-es-bulk-flusher");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::flushSafely,
                properties.getFlushIntervalMs(),
                properties.getFlushIntervalMs(),
                TimeUnit.MILLISECONDS);
        log.info("ES bulk index enabled: maxSize={}, flushIntervalMs={}, queueCapacity={}",
                properties.getMaxSize(), properties.getFlushIntervalMs(), properties.getQueueCapacity());
    }

    public void index(String indexName, String id, Object document) {
        if (!properties.isEnabled()) {
            indexOne(indexName, id, document);
            return;
        }
        IndexCommand command = new IndexCommand(indexName, id, document);
        if (!queue.offer(command)) {
            log.warn("ES bulk queue full, fallback to sync index: index={}, id={}", indexName, id);
            indexOne(indexName, id, document);
            return;
        }
        if (queue.size() >= properties.getMaxSize()) {
            flushSafely();
        }
    }

    private void flushSafely() {
        try {
            flush();
        } catch (Exception e) {
            log.error("ES bulk flush failed", e);
        }
    }

    private void flush() throws Exception {
        if (queue == null || queue.isEmpty()) {
            return;
        }
        List<IndexCommand> batch = new ArrayList<>(properties.getMaxSize());
        queue.drainTo(batch, properties.getMaxSize());
        if (batch.isEmpty()) {
            return;
        }
        List<BulkOperation> operations = batch.stream()
                .map(command -> BulkOperation.of(op -> op.index(i -> {
                    i.index(command.indexName).document(command.document);
                    // id 为空时不设 .id()，由 ES 自动生成（PUT /_doc/<id> 不允许尾斜杠）
                    if (command.id != null && !command.id.isEmpty()) {
                        i.id(command.id);
                    }
                    return i;
                })))
                .toList();
        var response = esClient.bulk(b -> b.operations(operations));
        if (response.errors()) {
            log.warn("ES bulk partial failure, retrying items one by one, batchSize={}", batch.size());
            for (IndexCommand command : batch) {
                indexOne(command.indexName, command.id, command.document);
            }
            return;
        }
        log.debug("ES bulk indexed: batchSize={}, took={}ms", batch.size(), response.took());
    }

    private void indexOne(String indexName, String id, Object document) {
        try {
            indexOnce(indexName, id, document);
        } catch (Exception e) {
            int retryTimes = Math.max(properties.getRetryTimes(), 0);
            for (int attempt = 1; attempt <= retryTimes; attempt++) {
                try {
                    indexOnce(indexName, id, document);
                    log.debug("ES index retry succeeded: index={}, id={}, attempt={}", indexName, id, attempt);
                    return;
                } catch (Exception retryException) {
                    e = retryException;
                }
            }
            log.error("ES index failed after retries: index={}, id={}, retryTimes={}", indexName, id, retryTimes, e);
        }
    }

    private void indexOnce(String indexName, String id, Object document) throws Exception {
        esClient.index(i -> {
            i.index(indexName).document(document);
            // id 为空时不设 .id()，由 ES 自动生成（PUT /_doc/<id> 不允许尾斜杠）
            if (id != null && !id.isEmpty()) {
                i.id(id);
            }
            return i;
        });
    }

    @PreDestroy
    public void shutdown() {
        flushSafely();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @AllArgsConstructor
    private static class IndexCommand {
        private String indexName;
        private String id;
        private Object document;
    }
}
