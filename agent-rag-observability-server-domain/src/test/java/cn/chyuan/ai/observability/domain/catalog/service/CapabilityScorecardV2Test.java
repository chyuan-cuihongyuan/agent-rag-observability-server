package cn.chyuan.ai.observability.domain.catalog.service;

import cn.chyuan.ai.observability.domain.catalog.CatalogServiceEntry;
import cn.chyuan.ai.observability.domain.catalog.service.CapabilityCatalogService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计分卡 v2 单测（工单 0245 AF9）：四门加权/缺门降级/空能力/总分均值。
 */
public class CapabilityScorecardV2Test {

    private static CatalogServiceEntry entry(String name, List<String> capabilities) {
        CatalogServiceEntry e = new CatalogServiceEntry();
        e.setName(name);
        e.setDescription("d");
        e.setPort(8092);
        e.setCapabilities(capabilities);
        e.setComponents(List.of());
        return e;
    }

    @Test
    void 四门全达标满分() {
        CapabilityCatalogService service = new CapabilityCatalogService(List.of());
        Map<String, Map<String, Map<String, Object>>> quality = Map.of(
                "svc", Map.of("rag", Map.of("tests", true, "docs", "docs/02/01.md",
                        "contract", true, "defaultOff", true)));
        Map<String, Object> out = service.scorecardV2(
                List.of(entry("svc", List.of("rag"))), quality);
        assertEquals(100L, out.get("overallScoreV2"));
        @SuppressWarnings("unchecked")
        Map<String, Object> svc = (Map<String, Object>) ((List<?>) out.get("services"))
                .stream().findFirst().orElseThrow();
        assertEquals(100L, svc.get("scoreV2"));
    }

    @Test
    void 缺门降级与权重口径() {
        CapabilityCatalogService service = new CapabilityCatalogService(List.of());
        // 只有 tests(40) + defaultOff(10) → 50
        Map<String, Map<String, Map<String, Object>>> quality = Map.of(
                "svc", Map.of("cap", Map.of("tests", true, "defaultOff", true)));
        Map<String, Object> out = service.scorecardV2(
                List.of(entry("svc", List.of("cap"))), quality);
        @SuppressWarnings("unchecked")
        Map<String, Object> svc = (Map<String, Object>) ((List<?>) out.get("services"))
                .stream().findFirst().orElseThrow();
        assertEquals(50L, svc.get("scoreV2"));
        // 无质量记录 → 全门 0
        Map<String, Object> out2 = service.scorecardV2(
                List.of(entry("svc2", List.of("cap"))), Map.of());
        assertEquals(0L, out2.get("overallScoreV2"));
    }

    @Test
    void 多能力均值与多服务总分() {
        CapabilityCatalogService service = new CapabilityCatalogService(List.of());
        Map<String, Map<String, Map<String, Object>>> quality = Map.of(
                "svc", Map.of(
                        "full", Map.of("tests", true, "docs", "d", "contract", true, "defaultOff", true),
                        "partial", Map.of("tests", true)));
        Map<String, Object> out = service.scorecardV2(
                List.of(entry("svc", List.of("full", "partial"))), quality);
        // 能力均值 (100+40)/2=70
        assertEquals(70L, out.get("overallScoreV2"));
        // docs 字符串 "false" 视为不达标
        Map<String, Map<String, Map<String, Object>>> negative = Map.of(
                "svc", Map.of("full", Map.of("tests", true, "docs", "false",
                        "contract", true, "defaultOff", true)));
        assertEquals(70L, service.scorecardV2(
                List.of(entry("svc", List.of("full"))), negative).get("overallScoreV2"));
        // 空能力列表安全
        assertTrue(service.scorecardV2(List.of(entry("empty", List.of())), Map.of())
                .containsKey("overallScoreV2"));
    }
}
