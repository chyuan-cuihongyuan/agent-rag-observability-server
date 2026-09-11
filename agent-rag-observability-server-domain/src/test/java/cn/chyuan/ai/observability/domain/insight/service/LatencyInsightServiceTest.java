package cn.chyuan.ai.observability.domain.insight.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 慢链路洞察服务单元测试（工单 0153 U7）— 分位数纯函数（已知答案）、TopN、空集语义。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("慢链路洞察服务测试")
class LatencyInsightServiceTest {

    @Mock
    private IChatResultRepository chatResultRepository;

    @InjectMocks
    private LatencyInsightService service;

    @Test
    @DisplayName("分位数纯函数 — 已知答案样本（最近邻秩）")
    public void testPercentileKnownAnswer() {
        List<Integer> values = List.of(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        assertEquals(50, LatencyInsightService.percentile(values, 50));
        assertEquals(100, LatencyInsightService.percentile(values, 95));
        assertEquals(100, LatencyInsightService.percentile(values, 100));
        assertEquals(10, LatencyInsightService.percentile(values, 1));
    }

    @Test
    @DisplayName("分位数边界 — 空集 null、单元素、p 越界拒绝")
    public void testPercentileEdge() {
        assertNull(LatencyInsightService.percentile(List.of(), 50));
        assertNull(LatencyInsightService.percentile(null, 50));
        assertEquals(7, LatencyInsightService.percentile(List.of(7), 95));
        assertThrows(IllegalArgumentException.class, () -> LatencyInsightService.percentile(List.of(1), 0));
        assertThrows(IllegalArgumentException.class, () -> LatencyInsightService.percentile(List.of(1), 101));
    }

    @Test
    @DisplayName("概览 — 计数与均值口径（null 耗时记录剔除）")
    public void testLatencyOverview() {
        when(chatResultRepository.queryCostSources(anyString(), anyInt())).thenReturn(List.of(
                chat(100), chat(200), chat(300), chat(null)
        ));

        Map<String, Object> row = service.latencyOverview(7);

        assertEquals(3, row.get("count"));
        assertEquals(200, row.get("p50"));
        assertEquals(300, row.get("p95"));
        assertEquals(200.0, (Double) row.get("avg"), 1e-9);
    }

    @Test
    @DisplayName("慢链路 TopN — 降序与钳制")
    public void testSlowTopN() {
        when(chatResultRepository.queryCostSources(anyString(), anyInt())).thenReturn(List.of(
                chat(100), chat(300), chat(200)
        ));

        List<Map<String, Object>> rows = service.slowTopN(7, 2);

        assertEquals(2, rows.size());
        assertEquals(300, rows.get(0).get("totalCostTimeMs"));
        assertEquals(200, rows.get(1).get("totalCostTimeMs"));
    }

    private ChatResultEntity chat(Integer costMs) {
        return ChatResultEntity.builder().totalCostTimeMs(costMs)
                .traceId("t").sessionId("s").agentId("a").build();
    }
}
