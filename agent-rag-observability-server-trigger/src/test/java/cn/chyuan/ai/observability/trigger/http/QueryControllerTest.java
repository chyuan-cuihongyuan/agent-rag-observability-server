package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.query.TraceQueryDTO;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.domain.observe.service.ObserveQueryService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Trace 查询控制器单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Trace 查询控制器测试")
public class QueryControllerTest {

    @Mock
    private ObserveQueryService observeQueryService;

    @InjectMocks
    private QueryController controller;

    @Test
    @DisplayName("按 traceId 查询 — 成功返回完整 Trace")
    public void testQueryTrace_Success() {
        // 准备
        AgentDecisionEntity decision = new AgentDecisionEntity();
        decision.setTraceId("trace-001");
        decision.setSessionId("session-001");
        decision.setOwnerUserId("user-001");
        decision.setSourceService("agent-service");
        when(observeQueryService.queryDecisionByTraceId("trace-001")).thenReturn(decision);
        when(observeQueryService.queryRetrievalByTraceId("trace-001")).thenReturn(null);
        when(observeQueryService.queryChatResultByTraceId("trace-001")).thenReturn(null);

        // 执行
        var result = controller.queryTrace("trace-001");

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertNotNull(result.getData(), "数据不应为 null");
        assertEquals("trace-001", result.getData().getTraceId(), "traceId 应正确");
        assertEquals("session-001", result.getData().getSessionId(), "sessionId 应正确");
    }

    @Test
    @DisplayName("按 traceId 查询 — 无效 traceId 返回参数错误")
    public void testQueryTrace_InvalidTraceId() {
        // 执行
        var result = controller.queryTrace("");

        // 验证
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode(), "响应码应为参数非法");
    }

    @Test
    @DisplayName("按 sessionId 查询 — 成功返回决策列表")
    public void testQueryBySession_Success() {
        // 准备
        when(observeQueryService.queryBySessionId("session-001", 1, 20))
                .thenReturn(Collections.emptyList());

        // 执行
        Response<List<AgentDecisionEntity>> result = controller.queryBySession("session-001", 1, 20);

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertNotNull(result.getData(), "数据不应为 null");
    }

    @Test
    @DisplayName("按 sessionId 查询 — 无效分页参数返回错误")
    public void testQueryBySession_InvalidPage() {
        // 执行
        Response<List<AgentDecisionEntity>> result = controller.queryBySession("session-001", -1, 20);

        // 验证
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode(), "响应码应为参数非法");
    }

    @Test
    @DisplayName("按 userId 查询 — 成功返回决策列表")
    public void testQueryByUser_Success() {
        // 准备
        when(observeQueryService.queryByUserId("tenant-001", "user-001", 1, 20))
                .thenReturn(Collections.emptyList());

        // 执行
        Response<List<AgentDecisionEntity>> result = controller.queryByUser("user-001", "tenant-001", 1, 20);

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        verify(observeQueryService).queryByUserId("tenant-001", "user-001", 1, 20);
    }

    @Test
    @DisplayName("按 userId 查询 — 无效 userId 返回参数错误")
    public void testQueryByUser_InvalidUserId() {
        // 执行
        Response<List<AgentDecisionEntity>> result = controller.queryByUser("", "tenant-001", 1, 20);

        // 验证
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode(), "响应码应为参数非法");
    }

    @Test
    @DisplayName("Trace 列表查询 — 成功返回分页结果")
    public void testQueryTraceList_Success() {
        // 准备
        TraceQueryDTO queryDTO = new TraceQueryDTO();
        queryDTO.setTenantId("tenant-001");
        queryDTO.setPage(1);
        queryDTO.setSize(20);
        when(observeQueryService.queryTraceList(anyMap(), eq(1), eq(20)))
                .thenReturn(Map.of("list", Collections.emptyList(), "total", 0));

        // 执行
        Response<Map<String, Object>> result = controller.queryTraceList(queryDTO);

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertTrue(result.getData().containsKey("list"), "应包含 list");
    }

    @Test
    @DisplayName("Trace 列表查询 — null 参数返回错误")
    public void testQueryTraceList_NullParam() {
        // 执行
        Response<Map<String, Object>> result = controller.queryTraceList(null);

        // 验证
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode(), "响应码应为参数非法");
    }
}
