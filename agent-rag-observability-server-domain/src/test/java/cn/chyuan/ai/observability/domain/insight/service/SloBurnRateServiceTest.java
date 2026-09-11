package cn.chyuan.ai.observability.domain.insight.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SLO 燃烧率纯函数单元测试（工单 0183 Y7）— 燃烧率计算、快烧/慢烧分级、边界。
 */
@DisplayName("SLO 燃烧率服务纯函数测试")
class SloBurnRateServiceTest {

    @Test
    @DisplayName("燃烧率 — 实际错误率 / 错误预算占比")
    public void testBurnRate() {
        // 错误率 0.6%，预算 0.1% → 6 倍
        assertEquals(6.0, SloBurnRateService.burnRate(1000, 6, 0.999), 1e-9);
        // 零错误 → 0
        assertEquals(0.0, SloBurnRateService.burnRate(1000, 0, 0.999), 1e-9);
        // 零请求 → 0
        assertEquals(0.0, SloBurnRateService.burnRate(0, 5, 0.999), 1e-9);
        // 目标 100%（预算 0）→ 有错误即哨兵无穷大
        assertEquals(Double.MAX_VALUE, SloBurnRateService.burnRate(100, 1, 1.0), 1e-9);
    }

    @Test
    @DisplayName("分级 — 快烧需双短窗、慢烧需长窗组合且非快烧")
    public void testSeverity() {
        // 1h/6h 都 ≥6 → PAGE
        assertEquals("PAGE", SloBurnRateService.severity(6, 6, 1, 6, 3));
        // 1h≥6 但 6h<6；6h≥3 且 3d≥3 → TICKET
        assertEquals("TICKET", SloBurnRateService.severity(8, 4, 3, 6, 3));
        // 都不超 → NONE
        assertEquals("NONE", SloBurnRateService.severity(1, 2, 2, 6, 3));
    }

    @Test
    @DisplayName("evaluate — 三窗输入装配与阈值透传")
    public void testEvaluate() {
        SloBurnRateService service = new SloBurnRateService();
        setField(service, "sloTarget", 0.999);
        setField(service, "fastThreshold", 6.0);
        setField(service, "slowThreshold", 3.0);

        Map<String, Object> result = service.evaluate(Map.of(
                "1h", new long[]{1000, 7},
                "6h", new long[]{6000, 48},
                "3d", new long[]{100000, 200}));

        assertEquals("PAGE", result.get("severity"));
        assertEquals(0.999, result.get("sloTarget"));
    }

    @Test
    @DisplayName("边界 — 空窗输入不抛出")
    public void testEvaluateEmptyWindows() {
        SloBurnRateService service = new SloBurnRateService();
        setField(service, "sloTarget", 0.999);
        setField(service, "fastThreshold", 6.0);
        setField(service, "slowThreshold", 3.0);

        Map<String, Object> result = service.evaluate(Map.of());
        assertEquals("NONE", result.get("severity"));
    }

    private void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
