package cn.chyuan.ai.observability.domain.catalog.service;

import cn.chyuan.ai.observability.domain.catalog.CatalogServiceEntry;
import cn.chyuan.ai.observability.domain.health.HealthProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 能力目录计分卡纯函数单元测试（工单 0184 Z1）— 配置解析、计分、降级。
 */
@DisplayName("能力目录计分卡测试")
class CapabilityCatalogServiceTest {

    private final CapabilityCatalogService service = new CapabilityCatalogService(List.of());

    private CatalogServiceEntry entry(String name, List<String> components) {
        return CatalogServiceEntry.builder().name(name).description("d")
                .capabilities(List.of("c1")).components(components).build();
    }

    private HealthProbe.ProbeResult pr(String component, boolean up) {
        return up ? HealthProbe.ProbeResult.ok(component, 5)
                : HealthProbe.ProbeResult.fail(component, 5, "err");
    }

    @Test
    @DisplayName("配置解析 — services 数组反序列化与非法容错")
    public void testParseCatalog() {
        List<CatalogServiceEntry> entries = service.parseCatalog(
                "{\"services\":[{\"name\":\"gw\",\"description\":\"网关\",\"port\":8099,"
                        + "\"capabilities\":[\"mcp\"],\"components\":[\"db\"]}]}");
        assertEquals(1, entries.size());
        assertEquals("gw", entries.get(0).getName());
        assertTrue(service.parseCatalog("not-json").isEmpty());
        assertTrue(service.parseCatalog("{}").isEmpty());
    }

    @Test
    @DisplayName("计分 — 全 UP=100、db DOWN=0(DOWN)、es DOWN=40(DEGRADED)、无探针不惩罚")
    public void testScorecard() {
        Map<String, HealthProbe.ProbeResult> health = Map.of(
                "db", pr("db", false),
                "es", pr("es", false));

        List<Map<String, Object>> rows = service.scorecard(
                List.of(entry("svc-db-down", List.of("db")),
                        entry("svc-es-down", List.of("es")),
                        entry("svc-unknown", List.of("vector"))),
                health);

        assertEquals(0L, rows.get(0).get("score"));
        assertEquals("DOWN", rows.get(0).get("status"));
        assertEquals(40L, rows.get(1).get("score"));
        assertEquals("DEGRADED", rows.get(1).get("status"));
        assertEquals(100L, rows.get(2).get("score")); // 无探针组件不惩罚
        assertEquals("UP", rows.get(2).get("status"));
    }

    @Test
    @DisplayName("空目录 — 总分 0 不抛错")
    public void testEmptyCatalog() {
        assertEquals(0, service.scorecard(List.of(), Map.of()).size());
    }
}
