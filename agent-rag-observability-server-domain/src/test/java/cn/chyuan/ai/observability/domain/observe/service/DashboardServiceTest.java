package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IAgentDecisionRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DashboardService 契约测试（工单 1151/1156）：
 * 统计透传条件、空检索率聚合与两位小数、null/空桶与异常降级路径。
 */
class DashboardServiceTest {

    private IAgentDecisionRepository agentDecisionRepository;
    private IRagRetrievalRepository ragRetrievalRepository;
    private IChatResultRepository chatResultRepository;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        agentDecisionRepository = mock(IAgentDecisionRepository.class);
        ragRetrievalRepository = mock(IRagRetrievalRepository.class);
        chatResultRepository = mock(IChatResultRepository.class);
        dashboardService = new DashboardService(agentDecisionRepository, ragRetrievalRepository, chatResultRepository);
    }

    @Test
    @DisplayName("countRequests 透传时间窗条件")
    void countRequestsPassesTimeWindow() {
        when(agentDecisionRepository.countByCondition(anyMap())).thenReturn(7L);

        assertThat(dashboardService.countRequests("09:00", "10:00")).isEqualTo(7L);
        verify(agentDecisionRepository).countByCondition(Map.of("startTime", "09:00", "endTime", "10:00"));
    }

    @Test
    @DisplayName("countFails 以 FAIL 状态过滤")
    void countFailsFiltersFailStatus() {
        when(agentDecisionRepository.countByCondition(anyMap())).thenReturn(2L);

        assertThat(dashboardService.countFails("09:00", "10:00")).isEqualTo(2L);
        verify(agentDecisionRepository).countByCondition(
                Map.of("agentStatus", "FAIL", "startTime", "09:00", "endTime", "10:00"));
    }

    @Test
    @DisplayName("emptyRetrievalRate 按桶聚合并保留两位小数")
    void emptyRetrievalRateAggregatesBucketsWithTwoDecimals() {
        when(ragRetrievalRepository.statEmptyRetrievalRate(anyString(), anyString())).thenReturn(List.of(
                Map.of("count", "6", "emptyRetrieval", "0"),
                Map.of("count", "4", "emptyRetrieval", "1")));

        double rate = dashboardService.emptyRetrievalRate("09:00", "10:00");

        assertThat(rate).isEqualTo(40.0);
    }

    @Test
    @DisplayName("emptyRetrievalRate 统计为 null 时降级返回 0")
    void emptyRetrievalRateReturnsZeroOnNullStats() {
        when(ragRetrievalRepository.statEmptyRetrievalRate(anyString(), anyString())).thenReturn(null);

        assertThat(dashboardService.emptyRetrievalRate("09:00", "10:00")).isZero();
    }

    @Test
    @DisplayName("emptyRetrievalRate 总量为 0 时返回 0 避免除零")
    void emptyRetrievalRateReturnsZeroOnZeroTotal() {
        when(ragRetrievalRepository.statEmptyRetrievalRate(anyString(), anyString()))
                .thenReturn(List.of(Map.of("count", "0", "emptyRetrieval", "0")));

        assertThat(dashboardService.emptyRetrievalRate("09:00", "10:00")).isZero();
    }

    @Test
    @DisplayName("emptyRetrievalRate 仓储异常时降级返回 0 且不外抛")
    void emptyRetrievalRateDegradesToZeroOnRepositoryFailure() {
        when(ragRetrievalRepository.statEmptyRetrievalRate(anyString(), anyString()))
                .thenThrow(new IllegalStateException("cache down"));

        assertThat(dashboardService.emptyRetrievalRate("09:00", "10:00")).isZero();
    }

    @Test
    @DisplayName("getTrend 透传时间窗与间隔")
    void getTrendPassesInterval() {
        List<Map<String, Object>> trend = List.of(Map.of("day", "2026-09-14", "cnt", "5"));
        when(chatResultRepository.statTrend(anyString(), anyString(), anyString())).thenReturn(trend);

        assertThat(dashboardService.getTrend("09:00", "10:00", "day")).isSameAs(trend);
        verify(chatResultRepository).statTrend("09:00", "10:00", "day");
    }

    @Test
    @DisplayName("分支/工具/错误榜均透传决策仓储")
    void branchToolErrorStatsDelegateToDecisionRepository() {
        List<Map<String, Object>> branch = List.of(Map.of("branch", "A"));
        List<Map<String, Object>> tool = List.of(Map.of("tool", "search"));
        List<Map<String, Object>> error = List.of(Map.of("err", "timeout"));
        when(agentDecisionRepository.statByBranchType(anyString(), anyString())).thenReturn(branch);
        when(agentDecisionRepository.statByToolUsage(anyString(), anyString())).thenReturn(tool);
        when(agentDecisionRepository.statErrorRanking(anyString(), anyString())).thenReturn(error);

        assertThat(dashboardService.getBranchDistribution("09:00", "10:00")).isSameAs(branch);
        assertThat(dashboardService.getToolUsage("09:00", "10:00")).isSameAs(tool);
        assertThat(dashboardService.getErrorRanking("09:00", "10:00")).isSameAs(error);
    }

    @Test
    @DisplayName("avgCostTime 透传结果仓储")
    void avgCostTimeDelegatesToChatResultRepository() {
        when(chatResultRepository.avgCostTime(anyString(), anyString())).thenReturn(123.4);

        assertThat(dashboardService.avgCostTime("09:00", "10:00")).isEqualTo(123.4);
    }
}
