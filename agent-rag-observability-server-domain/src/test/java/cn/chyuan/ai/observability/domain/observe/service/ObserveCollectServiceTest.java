package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.cache.ICachePort;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IAgentDecisionRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IMemoryRecallLogRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IToolCallLogRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ToolCallLogEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * ObserveCollectService 采集落库契约测试（工单 1140）：
 * 缺省 createTime 按秒级格式补齐；各数据类型写入对应仓储并递增对应计数键。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ObserveCollectService 采集契约")
class ObserveCollectServiceTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
    @Mock
    private ICachePort cachePort;

    @InjectMocks
    private ObserveCollectService service;

    @Test
    @DisplayName("agent decision：缺省时间补齐、落库、计数键 agent_decision")
    void collectAgentDecisionDefaultsTimeAndCounts() {
        AgentDecisionEntity entity = new AgentDecisionEntity();

        service.collectAgentDecision(entity);

        ArgumentCaptor<AgentDecisionEntity> captor = ArgumentCaptor.forClass(AgentDecisionEntity.class);
        verify(agentDecisionRepository).save(captor.capture());
        assertThat(captor.getValue().getCreateTime()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        verify(cachePort).increment("agent_decision");
    }

    @Test
    @DisplayName("已带 createTime 的实体保持原值不被覆盖")
    void existingCreateTimePreserved() {
        AgentDecisionEntity entity = new AgentDecisionEntity();
        entity.setCreateTime("2026-01-02 03:04:05");

        service.collectAgentDecision(entity);

        ArgumentCaptor<AgentDecisionEntity> captor = ArgumentCaptor.forClass(AgentDecisionEntity.class);
        verify(agentDecisionRepository).save(captor.capture());
        assertThat(captor.getValue().getCreateTime()).isEqualTo("2026-01-02 03:04:05");
        verify(cachePort).increment("agent_decision");
    }

    @Test
    @DisplayName("chat result 与 rag retrieval 各走各的仓储与计数键")
    void chatResultAndRagRetrievalRouteCorrectly() {
        service.collectChatResult(new ChatResultEntity());
        verify(chatResultRepository).save(any());
        verify(cachePort).increment("chat_result");

        service.collectRagRetrieval(new RagRetrievalEntity());
        verify(ragRetrievalRepository).save(any());
        verify(cachePort).increment("rag_retrieval");

        verifyNoInteractions(memoryRecallLogRepository);
    }

    @Test
    @DisplayName("tool call log 走专属仓储与计数键")
    void toolCallLogRoutesCorrectly() {
        ToolCallLogEntity entity = new ToolCallLogEntity();

        service.collectToolCallLog(entity);

        verify(toolCallLogRepository).save(entity);
        verify(cachePort).increment("tool_call_log");
    }
}
