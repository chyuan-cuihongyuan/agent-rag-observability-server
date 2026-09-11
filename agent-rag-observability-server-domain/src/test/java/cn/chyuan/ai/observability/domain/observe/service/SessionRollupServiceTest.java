package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IAgentDecisionRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.valobj.SessionRollup;
import cn.chyuan.ai.observability.domain.observe.model.valobj.SessionSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话聚合视图服务单元测试（工单 0147 U1）— 聚合纯函数、空会话语义、近期会话钳制。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("会话聚合视图服务测试")
class SessionRollupServiceTest {

    @Mock
    private IAgentDecisionRepository agentDecisionRepository;

    @InjectMocks
    private SessionRollupService service;

    private AgentDecisionEntity trace(String traceId, String time, String status, Integer costMs, String agentId) {
        return AgentDecisionEntity.builder()
                .traceId(traceId).createTime(time).agentStatus(status)
                .costTimeMs(costMs).agentId(agentId).sessionId("s-1")
                .build();
    }

    @Test
    @DisplayName("聚合纯函数 — 多轮乱序输入按时间升序推导首末/成败/总耗时/去重 agent")
    public void testSummarize() {
        SessionRollup rollup = service.summarize("s-1", List.of(
                trace("t2", "2026-09-11 10:00:05", "SUCCESS", 1200, "agent-A"),
                trace("t1", "2026-09-11 10:00:01", "SUCCESS", 800, "agent-A"),
                trace("t3", "2026-09-11 10:00:10", "FAIL", null, "agent-B")
        ));

        assertEquals("s-1", rollup.getSessionId());
        assertEquals(3, rollup.getRounds());
        assertEquals("2026-09-11 10:00:01", rollup.getFirstTime());
        assertEquals("2026-09-11 10:00:10", rollup.getLastTime());
        assertEquals(2000L, rollup.getTotalCostMs());
        assertEquals(2, rollup.getSuccessCount());
        assertEquals(1, rollup.getFailCount());
        assertEquals(List.of("agent-A", "agent-B"), rollup.getAgents());
        assertEquals(List.of("t1", "t2", "t3"), rollup.getTraceIds());
    }

    @Test
    @DisplayName("全部无耗时 — totalCostMs 为 null 不造假")
    public void testSummarizeNoCost() {
        SessionRollup rollup = service.summarize("s-1", List.of(
                trace("t1", "2026-09-11 10:00:01", "SUCCESS", null, "agent-A")
        ));

        assertNull(rollup.getTotalCostMs());
        assertEquals(1, rollup.getSuccessCount());
    }

    @Test
    @DisplayName("空状态从严按失败统计")
    public void testNullStatusCountsAsFail() {
        SessionRollup rollup = service.summarize("s-1", List.of(
                trace("t1", "2026-09-11 10:00:01", null, 100, "agent-A")
        ));

        assertEquals(0, rollup.getSuccessCount());
        assertEquals(1, rollup.getFailCount());
    }

    @Test
    @DisplayName("会话无轨迹返回 null")
    public void testQueryRollupEmpty() {
        when(agentDecisionRepository.queryBySessionId("s-404", 1, 500)).thenReturn(List.of());

        assertNull(service.queryRollup("s-404"));
    }

    @Test
    @DisplayName("近期会话 — limit 钳制 [1,100]")
    public void testRecentSessionsClamp() {
        when(agentDecisionRepository.queryRecentSessions(50)).thenReturn(List.of(
                SessionSummary.builder().sessionId("s-9").traceCount(3).lastTime("2026-09-11 10:00:00").build()
        ));

        service.queryRecentSessions(50);
        service.queryRecentSessions(0);
        service.queryRecentSessions(999);

        verify(agentDecisionRepository).queryRecentSessions(50);
        verify(agentDecisionRepository).queryRecentSessions(1);
        verify(agentDecisionRepository).queryRecentSessions(100);
    }
}
