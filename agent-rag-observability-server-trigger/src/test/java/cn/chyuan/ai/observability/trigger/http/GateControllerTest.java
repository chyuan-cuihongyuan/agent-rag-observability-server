package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.evaluate.GateDTO;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateRecordEntity;
import cn.chyuan.ai.observability.domain.evaluate.service.GateService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 门禁控制器单元测试（工单 0136 R4）— CRUD 端点与门禁记录查询端点。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("门禁控制器测试")
class GateControllerTest {

    @Mock
    private GateService gateService;

    @InjectMocks
    private GateController controller;

    private GateEntity entity() {
        return GateEntity.builder()
                .gateId("gate-1").name("发布门禁")
                .safetyDimsJson("{\"hallucination\":0.8}")
                .scoreThresholdsJson("{\"overall\":0.6}")
                .trials(3).enabled(true)
                .createTime("2026-09-10 00:00:00").updateTime("2026-09-10 00:00:00")
                .build();
    }

    @Test
    @DisplayName("创建门禁 — 成功返回 gateId，规则字段透传服务层")
    public void testCreateSuccess() {
        when(gateService.create(any(GateEntity.class))).thenReturn(entity());
        GateDTO dto = GateDTO.builder()
                .name("发布门禁")
                .safetyDimsJson("{\"hallucination\":0.8}")
                .scoreThresholdsJson("{\"overall\":0.6}")
                .trials(3).build();

        Response<String> result = controller.create(dto);

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        assertEquals("gate-1", result.getData());
        ArgumentCaptor<GateEntity> captor = ArgumentCaptor.forClass(GateEntity.class);
        verify(gateService).create(captor.capture());
        assertEquals("{\"hallucination\":0.8}", captor.getValue().getSafetyDimsJson());
        assertEquals(3, captor.getValue().getTrials());
    }

    @Test
    @DisplayName("创建门禁 — name 为空返回参数错误；服务层校验失败透传错误信息")
    public void testCreateValidation() {
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, controller.create(GateDTO.builder().name(" ").build()).getCode());
        verify(gateService, never()).create(any());

        when(gateService.create(any(GateEntity.class)))
                .thenThrow(new IllegalArgumentException("safetyDims 维度不存在: foo"));
        Response<String> result = controller.create(GateDTO.builder().name("发布门禁")
                .safetyDimsJson("{\"foo\":0.8}").build());
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode());
        assertTrue(result.getInfo().contains("维度不存在"));
    }

    @Test
    @DisplayName("更新门禁 — gateId 校验后透传服务层")
    public void testUpdate() {
        Response<String> result = controller.update(GateDTO.builder()
                .gateId("gate-1").trials(5).build());
        assertEquals(ResponseCode.SUCCESS, result.getCode());
        verify(gateService).update(any(GateEntity.class));

        assertEquals(ResponseCode.ILLEGAL_PARAMETER, controller.update(GateDTO.builder().build()).getCode(),
                "gateId 缺失返回参数错误");
    }

    @Test
    @DisplayName("删除门禁 — 委托服务（停用语义），不存在返回参数错误")
    public void testDelete() {
        assertEquals(ResponseCode.SUCCESS, controller.delete("gate-1").getCode());
        verify(gateService).delete("gate-1");

        doThrow(new IllegalArgumentException("Gate 不存在: g-none")).when(gateService).delete("g-none");
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, controller.delete("g-none").getCode());
    }

    @Test
    @DisplayName("规则查询 — 详情/列表端点返回 DTO 字段齐全")
    public void testQueryEndpoints() {
        when(gateService.query("gate-1")).thenReturn(entity());
        Response<GateDTO> detail = controller.query("gate-1");
        assertEquals(ResponseCode.SUCCESS, detail.getCode());
        assertEquals("gate-1", detail.getData().getGateId());
        assertEquals(3, detail.getData().getTrials());
        assertTrue(detail.getData().getEnabled());

        when(gateService.queryList(1, 20)).thenReturn(List.of(entity()));
        Response<?> list = controller.list(1, 20);
        assertEquals(ResponseCode.SUCCESS, list.getCode());
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, controller.list(0, 20).getCode());
    }

    @Test
    @DisplayName("门禁记录查询 — 最新/历史/按任务三端点委托服务（gateId 可空查全部）")
    public void testRecordQueryEndpoints() {
        when(gateService.queryLatestRecord("gate-1")).thenReturn(GateRecordEntity.builder()
                .recordId("r-1").gateId("gate-1").taskId("task-1").result("BLOCK")
                .triggerDetail("[{\"ruleType\":\"SAFETY\"}]").createTime("2026-09-10 00:00:00").build());
        assertEquals("BLOCK", controller.latestRecord("gate-1").getData().getResult());

        when(gateService.queryRecordList(isNull(), eq(1), eq(20))).thenReturn(List.of());
        assertEquals(ResponseCode.SUCCESS, controller.recordList(null, 1, 20).getCode());
        verify(gateService).queryRecordList(isNull(), eq(1), eq(20));

        when(gateService.queryRecordByTaskId("task-1")).thenReturn(null);
        assertNull(controller.recordByTask("task-1").getData(), "无记录返回 null data");
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, controller.recordByTask("bad id!").getCode(),
                "非法 taskId 返回参数错误");
    }
}
