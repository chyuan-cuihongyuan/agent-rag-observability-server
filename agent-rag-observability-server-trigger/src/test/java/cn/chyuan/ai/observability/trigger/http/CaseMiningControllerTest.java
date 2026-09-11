package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.mining.CaseCandidateDTO;
import cn.chyuan.ai.observability.api.dto.mining.CaseDispositionRequestDTO;
import cn.chyuan.ai.observability.domain.mining.model.entity.CaseCandidateEntity;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseSource;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseStatus;
import cn.chyuan.ai.observability.domain.mining.service.CaseCandidateQueryService;
import cn.chyuan.ai.observability.domain.mining.service.CaseMiningService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Case 挖掘控制器单元测试（工单 0138 S2）— 候选查询/采集/回填/忽略合同。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Case 挖掘控制器测试")
class CaseMiningControllerTest {

    @Mock
    private CaseMiningService caseMiningService;

    @Mock
    private CaseCandidateQueryService candidateQueryService;

    @InjectMocks
    private CaseMiningController controller;

    @Test
    @DisplayName("候选列表 — 来源过滤透传与非法来源拒绝")
    public void testCandidates() {
        when(candidateQueryService.queryList(CaseSource.TRACE_FAIL, null, 1, 20)).thenReturn(List.of(
                CaseCandidateEntity.builder().id(1L).source(CaseSource.TRACE_FAIL).sourceRef("t-1")
                        .status(CaseStatus.PENDING).query("失败查询").build()
        ));

        Response<List<CaseCandidateDTO>> ok = controller.candidates("TRACE_FAIL", 1, 20);
        assertEquals(ResponseCode.SUCCESS, ok.getCode());
        assertEquals("TRACE_FAIL", ok.getData().get(0).getSource());
        assertEquals("PENDING", ok.getData().get(0).getStatus());

        Response<List<CaseCandidateDTO>> bad = controller.candidates("NOPE", 1, 20);
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, bad.getCode());
    }

    @Test
    @DisplayName("手动采集 — 计数汇总返回")
    public void testCollect() {
        when(caseMiningService.collect(any())).thenReturn(new CaseMiningService.CollectOutcome() {{
            evalLowScore = 2;
            traceFail = 1;
            patrolFail = 0;
        }});

        Response<Map<String, Object>> result = controller.collect(Map.of("lowScoreThreshold", 0.5));
        assertEquals(ResponseCode.SUCCESS, result.getCode());
        assertEquals(3, result.getData().get("total"));
        assertEquals(2, result.getData().get("evalLowScore"));
    }

    @Test
    @DisplayName("批量回填 — ids 必填、成功返回 promoted 与 datasetId")
    public void testPromote() {
        when(caseMiningService.promote(eq(List.of(1L, 2L)), isNull()))
                .thenReturn(new CaseMiningService.PromoteOutcome(2, "ds-w9"));

        Response<Map<String, Object>> empty = controller.promote(CaseDispositionRequestDTO.builder().build());
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, empty.getCode());

        Response<Map<String, Object>> ok = controller.promote(CaseDispositionRequestDTO.builder()
                .ids(List.of(1L, 2L)).build());
        assertEquals(ResponseCode.SUCCESS, ok.getCode());
        assertEquals(2, ok.getData().get("promoted"));
        assertEquals("ds-w9", ok.getData().get("datasetId"));
    }

    @Test
    @DisplayName("批量忽略 — ids 必填与计数透传")
    public void testIgnore() {
        when(caseMiningService.ignore(List.of(3L))).thenReturn(1);

        assertEquals(ResponseCode.ILLEGAL_PARAMETER, controller.ignore(null).getCode());
        Response<Map<String, Object>> ok = controller.ignore(CaseDispositionRequestDTO.builder().ids(List.of(3L)).build());
        assertEquals(ResponseCode.SUCCESS, ok.getCode());
        assertEquals(1, ok.getData().get("ignored"));
    }
}
