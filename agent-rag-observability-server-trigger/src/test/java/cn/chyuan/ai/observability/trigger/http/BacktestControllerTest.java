package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.evaluate.BacktestRequestDTO;
import cn.chyuan.ai.observability.domain.evaluate.service.BacktestService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 回测触发控制器单元测试（工单 0136 R4）— 入参校验与服务委托。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("回测触发控制器测试")
class BacktestControllerTest {

    @Mock
    private BacktestService backtestService;

    @InjectMocks
    private BacktestController controller;

    @Test
    @DisplayName("触发回测 — datasetId 模式成功返回 taskId/gateId/datasetId")
    public void testTriggerByDataset() {
        when(backtestService.triggerBacktest("ds-1", null, "gate-1", null, null, null))
                .thenReturn(Map.of("taskId", "task-001", "gateId", "gate-1", "datasetId", "ds-1"));

        Response<Map<String, String>> result = controller.trigger(BacktestRequestDTO.builder()
                .datasetId("ds-1").gateId("gate-1").build());

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        assertEquals("task-001", result.getData().get("taskId"));
        assertEquals("gate-1", result.getData().get("gateId"));
    }

    @Test
    @DisplayName("触发回测 — pool 模式与 evalType 透传")
    public void testTriggerByPool() {
        when(backtestService.triggerBacktest(isNull(), eq("golden"), eq("gate-1"), eq("RAG_RETRIEVAL"),
                eq("model-v2"), eq(null)))
                .thenReturn(Map.of("taskId", "task-002", "gateId", "gate-1", "datasetId", "ds-9"));

        Response<Map<String, String>> result = controller.trigger(BacktestRequestDTO.builder()
                .pool("golden").gateId("gate-1").evalType("RAG_RETRIEVAL").modelVersion("model-v2").build());

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        assertEquals("task-002", result.getData().get("taskId"));
    }

    @Test
    @DisplayName("入参校验 — gateId 缺失/datasetId 与 pool 缺一/非法 datasetId 返回参数错误")
    public void testValidation() {
        // gateId 缺失
        assertEquals(ResponseCode.ILLEGAL_PARAMETER,
                controller.trigger(BacktestRequestDTO.builder().datasetId("ds-1").build()).getCode());
        // datasetId 与 pool 都缺
        assertEquals(ResponseCode.ILLEGAL_PARAMETER,
                controller.trigger(BacktestRequestDTO.builder().gateId("gate-1").build()).getCode());
        // 非法 datasetId 格式
        assertEquals(ResponseCode.ILLEGAL_PARAMETER,
                controller.trigger(BacktestRequestDTO.builder()
                        .datasetId("bad id!").gateId("gate-1").build()).getCode());
        verify(backtestService, never()).triggerBacktest(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("服务层业务异常 — 透传为参数错误（gate 停用/数据集不存在等）")
    public void testServiceExceptionMapped() {
        when(backtestService.triggerBacktest(isNull(), eq("golden"), eq("gate-1"), isNull(), isNull(), isNull()))
                .thenThrow(new IllegalArgumentException("样本池内无数据集: golden"));

        Response<Map<String, String>> result = controller.trigger(BacktestRequestDTO.builder()
                .pool("golden").gateId("gate-1").build());

        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode());
        assertTrue(result.getInfo().contains("样本池内无数据集"));
    }
}
