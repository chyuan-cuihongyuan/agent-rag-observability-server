package cn.chyuan.ai.observability.infrastructure.mq.consumer;

import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.MemoryRecallLogEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ToolCallLogEntity;
import cn.chyuan.ai.observability.domain.observe.service.ObserveCollectService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TraceMessageConsumerTest {

    private final ObserveCollectService collectService = mock(ObserveCollectService.class);
    private final TraceMessageConsumer consumer = new TraceMessageConsumer();

    TraceMessageConsumerTest() {
        ReflectionTestUtils.setField(consumer, "observeCollectService", collectService);
    }

    @Test
    void routesExplicitMessageTypes() {
        consumer.onMessage("{\"messageType\":\"decision\",\"traceId\":\"t1\",\"intentType\":\"diagnose\"}");
        consumer.onMessage("{\"messageType\":\"retrieval\",\"traceId\":\"t2\",\"retrievalTopk\":5}");
        consumer.onMessage("{\"messageType\":\"chat_result\",\"traceId\":\"t3\",\"question\":\"q\",\"answer\":\"a\"}");
        consumer.onMessage("{\"messageType\":\"tool_call\",\"traceId\":\"t4\",\"toolName\":\"prometheus_query\",\"toolOutput\":\"ok\",\"status\":\"SUCCESS\"}");
        consumer.onMessage("{\"messageType\":\"memory_recall\",\"traceId\":\"t5\",\"queryText\":\"test\",\"sessionMemoryCount\":3}");

        verify(collectService).collectAgentDecision(any(AgentDecisionEntity.class));
        verify(collectService).collectRagRetrieval(any(RagRetrievalEntity.class));
        verify(collectService).collectChatResult(any(ChatResultEntity.class));
        verify(collectService).collectToolCallLog(any(ToolCallLogEntity.class));
        verify(collectService).collectMemoryRecallLog(any(MemoryRecallLogEntity.class));
    }

    @Test
    void ignoresUnknownMessagesWithoutCollecting() {
        consumer.onMessage("{\"traceId\":\"unknown\"}");

        verify(collectService, never()).collectAgentDecision(any());
        verify(collectService, never()).collectRagRetrieval(any());
        verify(collectService, never()).collectChatResult(any());
        verify(collectService, never()).collectToolCallLog(any());
        verify(collectService, never()).collectMemoryRecallLog(any());
    }
}
