package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.query.DashboardDTO;
import cn.chyuan.ai.observability.domain.observe.service.DashboardService;
import cn.chyuan.ai.observability.infrastructure.redis.DashboardCacheService;
import cn.chyuan.ai.observability.types.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DashboardController 单元测试
 * 测试仪表盘相关的所有接口，包括缓存命中和未命中的场景
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardController 仪表盘接口测试")
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private DashboardCacheService cacheService;

    @InjectMocks
    private DashboardController dashboardController;

    @Test
    @DisplayName("概览接口 - 缓存未命中时从 service 获取数据")
    void overview_cacheMiss() {
        // 模拟缓存未命中
        when(cacheService.get(anyString())).thenReturn(null);
        // 模拟 service 返回数据
        when(dashboardService.countRequests(anyString(), anyString())).thenReturn(100L);
        when(dashboardService.countFails(anyString(), anyString())).thenReturn(5L);
        when(dashboardService.avgCostTime(anyString(), anyString())).thenReturn(1500.0);
        when(dashboardService.emptyRetrievalRate(anyString(), anyString())).thenReturn(10.0);

        // 执行测试
        Response<DashboardDTO.Overview> response = dashboardController.overview(1);

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertNotNull(response.getData());

        DashboardDTO.Overview overview = response.getData();
        assertEquals(100L, overview.getTotalRequests());
        assertTrue(overview.getSuccessRate() > 0);
        assertTrue(overview.getAvgCostTimeMs() > 0);
        assertTrue(overview.getEmptyRetrievalRate() >= 0);

        // 验证 service 方法被调用
        verify(dashboardService, times(1)).countRequests(anyString(), anyString());
        verify(dashboardService, times(1)).countFails(anyString(), anyString());
        verify(dashboardService, times(1)).avgCostTime(anyString(), anyString());
        verify(dashboardService, times(1)).emptyRetrievalRate(anyString(), anyString());

        // 验证结果被缓存
        verify(cacheService, times(1)).cache(startsWith("overview:"), anyString());
    }

    @Test
    @DisplayName("概览接口 - 缓存命中时直接返回缓存数据")
    void overview_cacheHit() {
        // 模拟缓存命中
        String cachedJson = "{\"totalRequests\":50,\"successRate\":95.0," +
                "\"avgCostTimeMs\":1200.0,\"emptyRetrievalRate\":5.0,\"failRate\":5.0,\"totalTokens\":0}";
        when(cacheService.get(anyString())).thenReturn(cachedJson);

        // 执行测试
        Response<DashboardDTO.Overview> response = dashboardController.overview(7);

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertNotNull(response.getData());
        assertEquals(50L, response.getData().getTotalRequests());
        assertEquals(95.0, response.getData().getSuccessRate());

        // 验证 service 方法未被调用（因为走了缓存）
        verify(dashboardService, never()).countRequests(anyString(), anyString());
        verify(dashboardService, never()).countFails(anyString(), anyString());
    }

    @Test
    @DisplayName("概览接口 - 参数校验：days 超过最大值90")
    void overview_invalidDays_tooLarge() {
        // days > 90，应返回参数错误
        Response<DashboardDTO.Overview> response = dashboardController.overview(91);

        // 验证返回参数错误
        assertNotNull(response);
        assertEquals("0002", response.getCode());
        assertTrue(response.getInfo().contains("days"));
    }

    @Test
    @DisplayName("概览接口 - 参数校验：days 小于最小值1")
    void overview_invalidDays_tooSmall() {
        // days < 1，应返回参数错误
        Response<DashboardDTO.Overview> response = dashboardController.overview(0);

        assertNotNull(response);
        assertEquals("0002", response.getCode());
        assertTrue(response.getInfo().contains("days"));
    }

    @Test
    @DisplayName("概览接口 - 总请求数为0时成功率应为100%")
    void overview_zeroRequests() {
        // 模拟缓存未命中
        when(cacheService.get(anyString())).thenReturn(null);
        // 模拟总请求为0
        when(dashboardService.countRequests(anyString(), anyString())).thenReturn(0L);
        when(dashboardService.countFails(anyString(), anyString())).thenReturn(0L);
        when(dashboardService.avgCostTime(anyString(), anyString())).thenReturn(0.0);
        when(dashboardService.emptyRetrievalRate(anyString(), anyString())).thenReturn(0.0);

        // 执行测试
        Response<DashboardDTO.Overview> response = dashboardController.overview(1);

        // 验证结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals(100.0, response.getData().getSuccessRate());
        assertEquals(0.0, response.getData().getFailRate());
    }

    @Test
    @DisplayName("趋势接口 - 缓存未命中时从 service 获取数据")
    void trend_cacheMiss() {
        // 模拟缓存未命中
        when(cacheService.get(anyString())).thenReturn(null);

        List<Map<String, Object>> trendData = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("time", "2025-01-01 10:00");
        item.put("count", 25);
        trendData.add(item);
        when(dashboardService.getTrend(anyString(), anyString(), eq("hour")))
                .thenReturn(trendData);

        // 执行测试
        Response<List<Map<String, Object>>> response = dashboardController.trend(1, "hour");

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        assertEquals(25, response.getData().get(0).get("count"));

        // 验证 service 被调用
        verify(dashboardService, times(1)).getTrend(anyString(), anyString(), eq("hour"));
        // 验证结果被缓存
        verify(cacheService, times(1)).cache(startsWith("trend:"), anyString());
    }

    @Test
    @DisplayName("趋势接口 - 缓存命中时直接返回")
    void trend_cacheHit() {
        // 模拟缓存命中
        String cachedJson = "[{\"time\":\"2025-01-01 10:00\",\"count\":30}]";
        when(cacheService.get(anyString())).thenReturn(cachedJson);

        // 执行测试
        Response<List<Map<String, Object>>> response = dashboardController.trend(7, "day");

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertNotNull(response.getData());

        // 验证 service 未被调用
        verify(dashboardService, never()).getTrend(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("趋势接口 - 参数校验：interval 非法")
    void trend_invalidInterval() {
        // interval 不在允许范围内
        Response<List<Map<String, Object>>> response = dashboardController.trend(1, "minute");

        assertNotNull(response);
        assertEquals("0002", response.getCode());
        assertTrue(response.getInfo().contains("interval"));
    }

    @Test
    @DisplayName("趋势接口 - 参数校验：days 超过范围")
    void trend_invalidDays() {
        Response<List<Map<String, Object>>> response = dashboardController.trend(100, "hour");

        assertNotNull(response);
        assertEquals("0002", response.getCode());
    }

    @Test
    @DisplayName("分支分布接口 - 缓存未命中时正常返回")
    void branchDistribution_cacheMiss() {
        when(cacheService.get(anyString())).thenReturn(null);

        List<Map<String, Object>> branchData = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("branchType", "REACT");
        item.put("count", 60);
        branchData.add(item);
        when(dashboardService.getBranchDistribution(anyString(), anyString()))
                .thenReturn(branchData);

        // 执行测试
        Response<List<Map<String, Object>>> response = dashboardController.branchDistribution(7);

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());

        // 验证 service 被调用
        verify(dashboardService, times(1)).getBranchDistribution(anyString(), anyString());
        verify(cacheService, times(1)).cache(startsWith("branch:"), anyString());
    }

    @Test
    @DisplayName("分支分布接口 - 缓存命中时直接返回")
    void branchDistribution_cacheHit() {
        String cachedJson = "[{\"branchType\":\"REACT\",\"count\":80}]";
        when(cacheService.get(anyString())).thenReturn(cachedJson);

        // 执行测试
        Response<List<Map<String, Object>>> response = dashboardController.branchDistribution(7);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        verify(dashboardService, never()).getBranchDistribution(anyString(), anyString());
    }

    @Test
    @DisplayName("工具使用接口 - 正常返回数据")
    void toolUsage_success() {
        when(cacheService.get(anyString())).thenReturn(null);

        List<Map<String, Object>> toolData = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("toolName", "prometheus_query");
        item.put("count", 45);
        toolData.add(item);
        when(dashboardService.getToolUsage(anyString(), anyString()))
                .thenReturn(toolData);

        // 执行测试
        Response<List<Map<String, Object>>> response = dashboardController.toolUsage(7);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        verify(dashboardService, times(1)).getToolUsage(anyString(), anyString());
        verify(cacheService, times(1)).cache(startsWith("tool:"), anyString());
    }

    @Test
    @DisplayName("工具使用接口 - 参数校验：days 非法")
    void toolUsage_invalidDays() {
        Response<List<Map<String, Object>>> response = dashboardController.toolUsage(0);

        assertNotNull(response);
        assertEquals("0002", response.getCode());
        verify(dashboardService, never()).getToolUsage(anyString(), anyString());
    }

    @Test
    @DisplayName("错误排名接口 - 正常返回数据（不走缓存）")
    void errorRanking_success() {
        List<Map<String, Object>> errorData = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("errorMessage", "超时");
        item.put("count", 10);
        errorData.add(item);
        when(dashboardService.getErrorRanking(anyString(), anyString()))
                .thenReturn(errorData);

        // 执行测试
        Response<List<Map<String, Object>>> response = dashboardController.errorRanking(7);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        assertEquals("超时", response.getData().get(0).get("errorMessage"));
        verify(dashboardService, times(1)).getErrorRanking(anyString(), anyString());
    }

    @Test
    @DisplayName("错误排名接口 - 参数校验：days 非法")
    void errorRanking_invalidDays() {
        Response<List<Map<String, Object>>> response = dashboardController.errorRanking(0);

        assertNotNull(response);
        assertEquals("0002", response.getCode());
        verify(dashboardService, never()).getErrorRanking(anyString(), anyString());
    }
}
