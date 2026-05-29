package cn.chyuan.ai.observability.infrastructure.es.bulk;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceBulkBufferTest {

    @Test
    void queuesDocumentsWhenBulkEnabledAndThresholdNotReached() {
        EsBulkProperties properties = new EsBulkProperties();
        properties.setEnabled(true);
        properties.setMaxSize(10);
        properties.setQueueCapacity(10);
        properties.setFlushIntervalMs(60_000);

        EsBulkIndexService service = new EsBulkIndexService();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "esClient", mock(ElasticsearchClient.class));

        service.init();
        service.index("trace-index", "trace-1", new TestDocument("trace-1"));

        BlockingQueue<?> queue = (BlockingQueue<?>) ReflectionTestUtils.getField(service, "queue");
        assertThat(queue).hasSize(1);
    }

    @Test
    void writesSynchronouslyWhenBulkDisabled() throws Exception {
        EsBulkProperties properties = new EsBulkProperties();
        properties.setEnabled(false);

        ElasticsearchClient client = mock(ElasticsearchClient.class);
        EsBulkIndexService service = new EsBulkIndexService();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "esClient", client);

        service.index("trace-index", "trace-1", new TestDocument("trace-1"));

        verify(client).index(any(java.util.function.Function.class));
    }

    private record TestDocument(String traceId) {
    }
}
