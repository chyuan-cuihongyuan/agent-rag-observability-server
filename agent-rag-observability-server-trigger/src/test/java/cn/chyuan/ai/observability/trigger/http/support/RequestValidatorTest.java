package cn.chyuan.ai.observability.trigger.http.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RequestValidator 契约测试（SELFLOOP2 loop-244）：
 * 6 个校验方法的 happy/boundary/illegal 三态语义锁定。
 */
@DisplayName("RequestValidator 校验契约")
class RequestValidatorTest {

    @Test
    @DisplayName("validateId：空/空白/超长/非法字符拒绝，合法 ID 通过")
    void validateId() {
        assertTrue(RequestValidator.validateId("traceId", null).contains("不能为空"));
        assertTrue(RequestValidator.validateId("traceId", "  ").contains("不能为空"));
        assertTrue(RequestValidator.validateId("traceId", "id with space").contains("格式非法"));
        assertTrue(RequestValidator.validateId("traceId", "a'.drop").contains("格式非法"));
        assertTrue(RequestValidator.validateId("traceId", "x".repeat(129)).contains("格式非法"));
        assertNull(RequestValidator.validateId("traceId", "trace-001_A.b:c"));
        assertNull(RequestValidator.validateId("traceId", "x".repeat(128)));
    }

    @Test
    @DisplayName("validateOptionalId：空值放行，非法值仍拒绝")
    void validateOptionalId() {
        assertNull(RequestValidator.validateOptionalId("sessionId", null));
        assertNull(RequestValidator.validateOptionalId("sessionId", ""));
        assertTrue(RequestValidator.validateOptionalId("sessionId", "bad id").contains("格式非法"));
        assertNull(RequestValidator.validateOptionalId("sessionId", "sess_1"));
    }

    @Test
    @DisplayName("validatePage：边界 1/10000 与 1/200 通过，越界拒绝")
    void validatePage() {
        assertNull(RequestValidator.validatePage(1, 1));
        assertNull(RequestValidator.validatePage(10000, 200));
        assertTrue(RequestValidator.validatePage(0, 20).contains("page"));
        assertTrue(RequestValidator.validatePage(10001, 20).contains("page"));
        assertTrue(RequestValidator.validatePage(1, 0).contains("size"));
        assertTrue(RequestValidator.validatePage(1, 201).contains("size"));
    }

    @Test
    @DisplayName("validateDays：边界 1/90 通过，0/91 拒绝")
    void validateDays() {
        assertNull(RequestValidator.validateDays(1));
        assertNull(RequestValidator.validateDays(90));
        assertTrue(RequestValidator.validateDays(0).contains("days"));
        assertTrue(RequestValidator.validateDays(91).contains("days"));
    }

    @Test
    @DisplayName("validateDashboardInterval：仅 hour/day")
    void validateDashboardInterval() {
        assertNull(RequestValidator.validateDashboardInterval("hour"));
        assertNull(RequestValidator.validateDashboardInterval("day"));
        assertTrue(RequestValidator.validateDashboardInterval("week").contains("hour或day"));
        assertTrue(RequestValidator.validateDashboardInterval(null).contains("hour或day"));
    }

    @Test
    @DisplayName("validateTimeRange：date-only/datetime/T 分隔/起止倒置/非法格式")
    void validateTimeRange() {
        assertNull(RequestValidator.validateTimeRange(null, null));
        assertNull(RequestValidator.validateTimeRange("2026-09-01", "2026-09-13"));
        assertNull(RequestValidator.validateTimeRange("2026-09-01T10:00:00", "2026-09-01 12:00:00"));
        assertTrue(RequestValidator.validateTimeRange("2026-09-13", "2026-09-01").contains("不能晚于"));
        assertTrue(RequestValidator.validateTimeRange("2026/09/01", null).contains("格式非法"));
        assertTrue(RequestValidator.validateTimeRange("2026-13-40", null).contains("不是有效日期时间"));
        assertTrue(RequestValidator.validateTimeRange(null, "bad").contains("格式非法"));
    }
}
