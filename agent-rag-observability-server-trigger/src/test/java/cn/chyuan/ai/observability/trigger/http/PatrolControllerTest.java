package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.patrol.PatrolRoundSummaryDTO;
import cn.chyuan.ai.observability.domain.patrol.model.entity.PatrolRecordEntity;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundSummary;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;
import cn.chyuan.ai.observability.domain.patrol.service.PatrolProbeService;
import cn.chyuan.ai.observability.domain.patrol.service.PatrolQueryService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 巡检拨测控制器单元测试（工单 0137 S1）— 记录分页/最近一轮/手动触发合同。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("巡检拨测控制器测试")
class PatrolControllerTest {

    @Mock
    private PatrolProbeService patrolProbeService;

    @Mock
    private PatrolQueryService patrolQueryService;

    @InjectMocks
    private PatrolController controller;

    @Test
    @DisplayName("记录分页 — 服务列表逐条转 DTO")
    public void testRecords() {
        when(patrolQueryService.queryRecords(1, 20)).thenReturn(List.of(
                PatrolRecordEntity.builder()
                        .id(9L).roundId("P1").taskRef("q-1").query("你好")
                        .status(PatrolStatus.SUCCESS).score(0.75).durationMs(120L)
                        .traceId("t-9").createTime("2026-09-11 10:00:00")
                        .build()
        ));

        Response<List<cn.chyuan.ai.observability.api.dto.patrol.PatrolRecordDTO>> result =
                controller.records(1, 20);

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("SUCCESS", result.getData().get(0).getStatus());
        assertEquals(0.75, result.getData().get(0).getScore());
        assertEquals("t-9", result.getData().get(0).getTraceId());
    }

    @Test
    @DisplayName("最近一轮 — 空库空汇总透传")
    public void testLatestEmpty() {
        when(patrolQueryService.queryLatestRound()).thenReturn(PatrolRoundSummary.builder()
                .roundId(null).total(0).success(0).fail(0).timeout(0).avgScore(null).finishedAt(null).build());

        Response<PatrolRoundSummaryDTO> result = controller.latest();

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        assertNull(result.getData().getRoundId());
        assertEquals(0, result.getData().getTotal());
    }

    @Test
    @DisplayName("手动触发 — 成功返回轮次汇总")
    public void testTriggerSuccess() {
        when(patrolProbeService.runRound(any())).thenReturn(PatrolRoundSummary.builder()
                .roundId("P42").total(3).success(2).fail(1).timeout(0).avgScore(0.8)
                .finishedAt("2026-09-11 10:05:00").build());

        Response<PatrolRoundSummaryDTO> result = controller.trigger();

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        assertEquals("P42", result.getData().getRoundId());
        assertEquals(3, result.getData().getTotal());
        assertEquals(2, result.getData().getSuccess());
        verify(patrolProbeService).runRound(any());
    }

    @Test
    @DisplayName("手动触发 — 服务异常返回系统错误且不抛出")
    public void testTriggerFailure() {
        when(patrolProbeService.runRound(any())).thenThrow(new IllegalStateException("线程池关闭"));

        Response<PatrolRoundSummaryDTO> result = controller.trigger();

        assertEquals(ResponseCode.ERROR, result.getCode());
        assertTrue(result.getInfo() != null && !result.getInfo().isEmpty());
    }
}
