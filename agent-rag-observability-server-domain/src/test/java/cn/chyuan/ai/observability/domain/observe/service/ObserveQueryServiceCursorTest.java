package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IAgentDecisionRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * trace 列表游标分页（SELFLOOP3 loop-344，工单 0486/0487）
 */
class ObserveQueryServiceCursorTest {

    private AgentDecisionEntity decision(String createTime, String traceId) {
        AgentDecisionEntity e = new AgentDecisionEntity();
        e.setCreateTime(createTime);
        e.setTraceId(traceId);
        return e;
    }

    private ObserveQueryService serviceWith(IAgentDecisionRepository repo) {
        return new ObserveQueryService(repo,
                mock(cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository.class),
                mock(cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository.class),
                mock(cn.chyuan.ai.observability.domain.observe.adapter.repository.IToolCallLogRepository.class),
                mock(cn.chyuan.ai.observability.domain.observe.adapter.repository.IMemoryRecallLogRepository.class));
    }

    @Test
    void fullPageProducesNextCursorFromLastRow() {
        IAgentDecisionRepository repo = mock(IAgentDecisionRepository.class);
        when(repo.queryByConditionAfter(any(), eq(2), isNull(), isNull()))
                .thenReturn(List.of(decision("2026-09-15 10:00:00", "t1"), decision("2026-09-15 09:59:58", "t2")));

        Map<String, Object> result = serviceWith(repo).queryTraceListAfter(Map.of(), 2, null);

        assertThat(result.get("nextCursor")).isEqualTo("2026-09-15 09:59:58|t2");
    }

    @Test
    void partialPageIsTerminalWithoutCursor() {
        IAgentDecisionRepository repo = mock(IAgentDecisionRepository.class);
        when(repo.queryByConditionAfter(any(), anyInt(), any(), any()))
                .thenReturn(List.of(decision("2026-09-15 10:00:00", "t1")));

        Map<String, Object> result = serviceWith(repo).queryTraceListAfter(Map.of(), 20, "2026-09-15 10:00:01|t0");

        assertThat(result.get("nextCursor")).isNull();
    }

    @Test
    void cursorIsParsedIntoSortValues() {
        IAgentDecisionRepository repo = mock(IAgentDecisionRepository.class);
        when(repo.queryByConditionAfter(any(), anyInt(), any(), any())).thenReturn(List.of());

        serviceWith(repo).queryTraceListAfter(Map.of(), 20, "2026-09-15 10:00:00|abc");

        verify(repo).queryByConditionAfter(any(), eq(20), eq("2026-09-15 10:00:00"), eq("abc"));
    }
}
