package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.*;
import cn.chyuan.ai.observability.domain.observe.model.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObserveQueryServiceTest {

    @Mock
    private IAgentDecisionRepository agentDecisionRepository;

    @Mock
    private IRagRetrievalRepository ragRetrievalRepository;

    @Mock
    private IChatResultRepository chatResultRepository;

    @Mock
    private IToolCallLogRepository toolCallLogRepository;

    @Mock
    private IMemoryRecallLogRepository memoryRecallLogRepository;

    private ObserveQueryService observeQueryService;

    @BeforeEach
    void setUp() {
        observeQueryService = new ObserveQueryService(
                agentDecisionRepository,
                ragRetrievalRepository,
                chatResultRepository,
                toolCallLogRepository,
                memoryRecallLogRepository
        );
    }

    @Test
    void testQueryDecisionByTraceId_Success() {
        // Given
        String traceId = "trace-123";
        AgentDecisionEntity decision = AgentDecisionEntity.builder()
                .traceId(traceId)
                .agentId("agent-1")
                .build();

        when(agentDecisionRepository.queryByTraceId(eq(traceId))).thenReturn(decision);

        // When
        AgentDecisionEntity result = observeQueryService.queryDecisionByTraceId(traceId);

        // Then
        assertNotNull(result);
        assertEquals(traceId, result.getTraceId());
        verify(agentDecisionRepository).queryByTraceId(eq(traceId));
    }

    @Test
    void testQueryDecisionByTraceId_NotFound() {
        // Given
        String traceId = "non-existent";
        when(agentDecisionRepository.queryByTraceId(eq(traceId))).thenReturn(null);

        // When
        AgentDecisionEntity result = observeQueryService.queryDecisionByTraceId(traceId);

        // Then
        assertNull(result);
    }

    @Test
    void testQueryRetrievalByTraceId_Success() {
        // Given
        String traceId = "trace-456";
        RagRetrievalEntity retrieval = RagRetrievalEntity.builder()
                .traceId(traceId)
                .queryText("test query")
                .build();

        when(ragRetrievalRepository.queryByTraceId(eq(traceId))).thenReturn(retrieval);

        // When
        RagRetrievalEntity result = observeQueryService.queryRetrievalByTraceId(traceId);

        // Then
        assertNotNull(result);
        assertEquals(traceId, result.getTraceId());
        verify(ragRetrievalRepository).queryByTraceId(eq(traceId));
    }

    @Test
    void testQueryChatResultByTraceId_Success() {
        // Given
        String traceId = "trace-789";
        ChatResultEntity chatResult = ChatResultEntity.builder()
                .traceId(traceId)
                .answer("test answer")
                .build();

        when(chatResultRepository.queryByTraceId(eq(traceId))).thenReturn(chatResult);

        // When
        ChatResultEntity result = observeQueryService.queryChatResultByTraceId(traceId);

        // Then
        assertNotNull(result);
        assertEquals(traceId, result.getTraceId());
        verify(chatResultRepository).queryByTraceId(eq(traceId));
    }

    @Test
    void testQueryToolCallsByTraceId_Success() {
        // Given
        String traceId = "trace-101";
        List<ToolCallLogEntity> toolCalls = Arrays.asList(
                ToolCallLogEntity.builder().traceId(traceId).toolName("tool1").build(),
                ToolCallLogEntity.builder().traceId(traceId).toolName("tool2").build()
        );

        when(toolCallLogRepository.queryByTraceId(eq(traceId))).thenReturn(toolCalls);

        // When
        List<ToolCallLogEntity> result = observeQueryService.queryToolCallsByTraceId(traceId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(toolCallLogRepository).queryByTraceId(eq(traceId));
    }

    @Test
    void testQueryToolCallsByTraceId_Empty() {
        // Given
        String traceId = "trace-102";
        when(toolCallLogRepository.queryByTraceId(eq(traceId))).thenReturn(Collections.emptyList());

        // When
        List<ToolCallLogEntity> result = observeQueryService.queryToolCallsByTraceId(traceId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testQueryMemoryRecallsByTraceId_Success() {
        // Given
        String traceId = "trace-103";
        List<MemoryRecallLogEntity> memoryRecalls = Arrays.asList(
                MemoryRecallLogEntity.builder().traceId(traceId).memoryId("mem1").build()
        );

        when(memoryRecallLogRepository.queryByTraceId(eq(traceId))).thenReturn(memoryRecalls);

        // When
        List<MemoryRecallLogEntity> result = observeQueryService.queryMemoryRecallsByTraceId(traceId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(memoryRecallLogRepository).queryByTraceId(eq(traceId));
    }

    @Test
    void testQueryTraceList_Success() {
        // Given
        Map<String, Object> condition = new HashMap<>();
        condition.put("agentId", "agent-1");
        int page = 1;
        int size = 10;

        List<AgentDecisionEntity> list = Arrays.asList(
                AgentDecisionEntity.builder().traceId("trace1").build(),
                AgentDecisionEntity.builder().traceId("trace2").build()
        );

        when(agentDecisionRepository.queryByCondition(eq(condition), eq(page), eq(size))).thenReturn(list);
        when(agentDecisionRepository.countByCondition(eq(condition))).thenReturn(2L);

        // When
        Map<String, Object> result = observeQueryService.queryTraceList(condition, page, size);

        // Then
        assertNotNull(result);
        assertEquals(list, result.get("list"));
        assertEquals(2L, result.get("total"));
        assertEquals(page, result.get("page"));
        assertEquals(size, result.get("size"));
        verify(agentDecisionRepository).queryByCondition(eq(condition), eq(page), eq(size));
        verify(agentDecisionRepository).countByCondition(eq(condition));
    }

    @Test
    void testQueryBySessionId_Success() {
        // Given
        String sessionId = "session-123";
        int page = 1;
        int size = 20;

        List<AgentDecisionEntity> list = Arrays.asList(
                AgentDecisionEntity.builder().sessionId(sessionId).build()
        );

        when(agentDecisionRepository.queryBySessionId(eq(sessionId), eq(page), eq(size))).thenReturn(list);

        // When
        List<AgentDecisionEntity> result = observeQueryService.queryBySessionId(sessionId, page, size);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(agentDecisionRepository).queryBySessionId(eq(sessionId), eq(page), eq(size));
    }

    @Test
    void testQueryByUserId_Success() {
        // Given
        String tenantId = "tenant-1";
        String ownerUserId = "user-1";
        int page = 1;
        int size = 10;

        List<AgentDecisionEntity> list = Arrays.asList(
                AgentDecisionEntity.builder().tenantId(tenantId).ownerUserId(ownerUserId).build()
        );

        when(agentDecisionRepository.queryByUserId(eq(tenantId), eq(ownerUserId), eq(page), eq(size))).thenReturn(list);

        // When
        List<AgentDecisionEntity> result = observeQueryService.queryByUserId(tenantId, ownerUserId, page, size);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(agentDecisionRepository).queryByUserId(eq(tenantId), eq(ownerUserId), eq(page), eq(size));
    }
}
