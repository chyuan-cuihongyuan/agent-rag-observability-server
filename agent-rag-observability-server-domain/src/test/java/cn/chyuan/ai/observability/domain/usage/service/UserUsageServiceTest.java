package cn.chyuan.ai.observability.domain.usage.service;

import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户/租户使用分析服务单元测试（工单 0149 U3）— 排行/并列稳定序/失败率/匿名桶。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户/租户使用分析服务测试")
class UserUsageServiceTest {

    @InjectMocks
    private UserUsageService service;

    private ChatResultEntity chat(String user, String tenant, String status, Integer costMs) {
        return ChatResultEntity.builder()
                .ownerUserId(user).tenantId(tenant).finalStatus(status)
                .totalCostTimeMs(costMs).build();
    }

    @Test
    @DisplayName("用户 TopN — 请求数降序、失败率与平均耗时口径")
    public void testTopUsers() {
        List<Map<String, Object>> rows = service.topUsers(List.of(
                chat("alice", "t1", "SUCCESS", 100),
                chat("alice", "t1", "FAIL", 300),
                chat("bob", "t1", "SUCCESS", 200),
                chat("carol", "t2", "SUCCESS", 50)
        ), 2);

        assertEquals(2, rows.size());
        assertEquals("alice", rows.get(0).get("ownerUserId"));
        assertEquals(2L, rows.get(0).get("requests"));
        assertEquals(0.5, (Double) rows.get(0).get("failRate"), 1e-9);
        assertEquals(200.0, (Double) rows.get(0).get("avgCostMs"), 1e-9);
        assertEquals("bob", rows.get(1).get("ownerUserId"));
    }

    @Test
    @DisplayName("并列请求数 — 按用户名字典序稳定排序")
    public void testTieStableOrder() {
        List<Map<String, Object>> rows = service.topUsers(List.of(
                chat("zoe", "t1", "SUCCESS", 1),
                chat("amy", "t1", "SUCCESS", 1)
        ), 10);

        assertEquals("amy", rows.get(0).get("ownerUserId"));
        assertEquals("zoe", rows.get(1).get("ownerUserId"));
    }

    @Test
    @DisplayName("匿名桶 — 空用户归 '-' 不丢弃")
    public void testAnonymousBucket() {
        List<Map<String, Object>> rows = service.topUsers(List.of(
                chat(null, "t1", "SUCCESS", 1),
                chat("  ", "t1", "SUCCESS", 1)
        ), 10);

        assertEquals(1, rows.size());
        assertEquals("-", rows.get(0).get("ownerUserId"));
        assertEquals(2L, rows.get(0).get("requests"));
    }

    @Test
    @DisplayName("租户汇总 — 调用量/失败数/总耗时")
    public void testTenantSummary() {
        List<Map<String, Object>> rows = service.tenantSummary(List.of(
                chat("u1", "tenant-A", "SUCCESS", 100),
                chat("u2", "tenant-A", "FAIL", 200),
                chat("u3", "tenant-B", "SUCCESS", null)
        ));

        assertEquals(2, rows.size());
        assertEquals("tenant-A", rows.get(0).get("tenantId"));
        assertEquals(2L, rows.get(0).get("requests"));
        assertEquals(1L, rows.get(0).get("fails"));
        assertEquals(300L, rows.get(0).get("totalCostMs"));
        assertEquals(0L, rows.get(1).get("fails"));
    }

    @Test
    @DisplayName("空窗 — 返回空列表不抛错")
    public void testEmptyWindow() {
        assertTrue(service.topUsers(List.of(), 10).isEmpty());
        assertTrue(service.tenantSummary(List.of()).isEmpty());
    }
}
