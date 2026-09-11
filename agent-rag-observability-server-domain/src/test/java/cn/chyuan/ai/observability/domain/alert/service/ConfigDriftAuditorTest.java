package cn.chyuan.ai.observability.domain.alert.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置漂移审计纯函数单元测试（工单 0182 Y6）— 字段级 diff、敏感脱敏、同值不计。
 */
@DisplayName("配置漂移审计纯函数测试")
class ConfigDriftAuditorTest {

    private final ConfigDriftAuditor service = new ConfigDriftAuditor(null);

    @Test
    @DisplayName("diff — 变化字段 from→to，同值不计")
    public void testDiff() {
        List<Map<String, String>> changes = service.diff(
                new java.util.LinkedHashMap<>(Map.of("name", "g1", "trials", "1", "enabled", "1")),
                new java.util.LinkedHashMap<>(Map.of("name", "g1-new", "trials", "1", "enabled", "0")));

        assertEquals(2, changes.size());
        // Map.of 无序，不假设字段顺序，按 field 查找断言
        Map<String, Map<String, String>> byField = new java.util.LinkedHashMap<>();
        changes.forEach(c -> byField.put(c.get("field"), c));
        assertEquals("g1", byField.get("name").get("from"));
        assertEquals("g1-new", byField.get("name").get("to"));
        assertEquals("0", byField.get("enabled").get("to"));
        assertFalse(byField.containsKey("trials"));
    }

    @Test
    @DisplayName("敏感脱敏 — apikey/token 值脱敏为 ***（长度）")
    public void testMask() {
        List<Map<String, String>> changes = service.diff(
                Map.of("apiKey", "old-secret"),
                Map.of("apiKey", "new-secret"));

        assertEquals(1, changes.size());
        assertEquals("***(10)", changes.get(0).get("from"));
        assertEquals("***(10)", changes.get(0).get("to"));
        assertNull(ConfigDriftAuditor.mask("apiKey", null));
        assertEquals("plain", ConfigDriftAuditor.mask("name", "plain"));
    }

    @Test
    @DisplayName("null 与空串视为不同状态；无变化返回空列表")
    public void testNullVsEmpty() {
        Map<String, String> before = new java.util.HashMap<>();
        before.put("a", null);
        assertEquals(1, service.diff(before, new java.util.HashMap<>(Map.of("a", ""))).size());
        assertTrue(service.diff(Map.of("a", "x"), Map.of("a", "x")).isEmpty());
        assertTrue(service.diff(null, Map.of("a", "x")).isEmpty());
    }

    @Test
    @DisplayName("toJson — 变更数组序列化与 null 值")
    public void testToJson() {
        Map<String, String> withNull = new java.util.HashMap<>();
        withNull.put("field", "note");
        withNull.put("from", null);
        withNull.put("to", "x|y");
        String json = ConfigDriftAuditor.toJson(List.of(
                Map.of("field", "name", "from", "a", "to", "b"),
                withNull));
        assertTrue(json.startsWith("[{"));
        assertTrue(json.contains("\"from\":null"));
        assertTrue(json.contains("x\\|y") || json.contains("x|y"));
    }
}
