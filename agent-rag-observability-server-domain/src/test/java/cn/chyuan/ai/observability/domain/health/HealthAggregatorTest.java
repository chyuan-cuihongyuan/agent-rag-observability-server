package cn.chyuan.ai.observability.domain.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 深度健康聚合纯函数单元测试（工单 0181 Y5）— UP/DEGRADED/DOWN 判定矩阵。
 */
@DisplayName("深度健康聚合测试")
class HealthAggregatorTest {

    private HealthProbe.ProbeResult result(String component, boolean up) {
        return up ? HealthProbe.ProbeResult.ok(component, 0)
                : HealthProbe.ProbeResult.fail(component, 10, "err");
    }

    @Test
    @DisplayName("全 UP → UP")
    public void testAllUp() {
        assertEquals(HealthAggregator.UP, HealthAggregator.overall(
                List.of(result("db", true), result("es", true), result("mq", true)),
                List.of(true, false, false)));
    }

    @Test
    @DisplayName("非关键组件 DOWN → DEGRADED")
    public void testDegraded() {
        assertEquals(HealthAggregator.DEGRADED, HealthAggregator.overall(
                List.of(result("db", true), result("es", false), result("mq", true)),
                List.of(true, false, false)));
    }

    @Test
    @DisplayName("关键组件（DB）DOWN → DOWN")
    public void testCriticalDown() {
        assertEquals(HealthAggregator.DOWN, HealthAggregator.overall(
                List.of(result("db", false), result("es", true)),
                List.of(true, false)));
    }

    @Test
    @DisplayName("全部 DOWN（含非关键）→ DEGRADED（无关键标记）")
    public void testAllNonCriticalDown() {
        assertEquals(HealthAggregator.DEGRADED, HealthAggregator.overall(
                List.of(result("es", false), result("mq", false)),
                List.of(false, false)));
    }
}
