package cn.chyuan.ai.observability.domain.cost.service;

import cn.chyuan.ai.observability.domain.cost.adapter.repository.IModelPricingRepository;
import cn.chyuan.ai.observability.domain.cost.model.entity.ModelPricingEntity;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 成本派生与聚合服务单元测试（工单 0148 U2）— 计价纯函数、按天/按 agent 聚合、未计价口径。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("成本派生与聚合服务测试")
class CostDeriveServiceTest {

    @Mock
    private IModelPricingRepository modelPricingRepository;

    @InjectMocks
    private CostDeriveService service;

    private ModelPricingEntity pricing(double in, double out) {
        return ModelPricingEntity.builder()
                .model("m-1").inputPricePer1k(in).outputPricePer1k(out).build();
    }

    @Test
    @DisplayName("计价纯函数 — 千 token 进位与四舍五入 6 位")
    public void testComputeCost() {
        // 1500 prompt × 0.002/1k = 0.003；2500 completion × 0.008/1k = 0.02 → 0.023
        assertEquals(0.023, service.computeCost("m-1", 1500, 2500, pricing(0.002, 0.008)), 1e-9);
        // 零 token
        assertEquals(0.0, service.computeCost("m-1", 0, 0, pricing(1, 1)), 1e-9);
        // null token 按 0
        assertEquals(0.0, service.computeCost("m-1", null, null, pricing(1, 1)), 1e-9);
        // 未配置计价返回 null
        assertNull(service.computeCost("m-1", 100, 100, null));
    }

    @Test
    @DisplayName("按天聚合 — 天键取 createTime 前 10 位，未计价附提示行")
    public void testAggregateDaily() {
        when(modelPricingRepository.queryByModel(anyString())).thenAnswer(inv ->
                "known".equals(inv.getArgument(0)) ? pricing(1, 2) : null);

        List<ChatResultEntity> chats = List.of(
                chat("2026-09-10 23:59:00", "a1", "known", 1000, 1000),
                chat("2026-09-11 00:01:00", "a1", "known", 2000, 0),
                chat("2026-09-11 08:00:00", "a2", "unknown-model", 9999, 9999)
        );

        List<Map<String, Object>> rows = service.aggregateDaily(chats);

        assertEquals(3, rows.size());
        assertEquals("2026-09-10", rows.get(0).get("day"));
        assertEquals(3.0, (Double) rows.get(0).get("cost"), 1e-9); // 1×1 + 1×2
        assertEquals("2026-09-11", rows.get(1).get("day"));
        assertEquals(2.0, (Double) rows.get(1).get("cost"), 1e-9); // 2×1
        assertEquals(1L, rows.get(2).get("unpricedRequests"));
    }

    @Test
    @DisplayName("按 agent 聚合 — 成本降序，未计价剔除")
    public void testAggregateByAgent() {
        when(modelPricingRepository.queryByModel(anyString())).thenReturn(pricing(1, 1));

        List<Map<String, Object>> rows = service.aggregateByAgent(List.of(
                chat("2026-09-11 08:00:00", "a-big", "known", 2000, 0),
                chat("2026-09-11 08:01:00", "a-small", "known", 500, 0),
                chat("2026-09-11 08:02:00", null, "known", 9000, 0)
        ));

        assertEquals(2, rows.size());
        assertEquals("a-big", rows.get(0).get("agentId"));
        assertEquals(2.0, (Double) rows.get(0).get("cost"), 1e-9);
        assertEquals(1L, rows.get(0).get("requests"));
    }

    @Test
    @DisplayName("未计价计数 — 无 modelVersion 或无计价配置")
    public void testCountUnpriced() {
        when(modelPricingRepository.queryByModel(anyString())).thenReturn(null);

        assertEquals(2, service.countUnpriced(List.of(
                chat("2026-09-11 08:00:00", "a1", null, 1, 1),
                chat("2026-09-11 08:00:00", "a1", "other", 1, 1)
        )));
    }

    private ChatResultEntity chat(String time, String agentId, String model, Integer prompt, Integer completion) {
        return ChatResultEntity.builder()
                .createTime(time).agentId(agentId).modelVersion(model)
                .promptTokens(prompt).completionTokens(completion)
                .build();
    }
}
