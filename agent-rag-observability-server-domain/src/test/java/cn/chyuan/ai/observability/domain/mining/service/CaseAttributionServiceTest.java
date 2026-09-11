package cn.chyuan.ai.observability.domain.mining.service;

import cn.chyuan.ai.observability.domain.mining.adapter.repository.ICaseCandidateRepository;
import cn.chyuan.ai.observability.domain.mining.model.entity.CaseCandidateEntity;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseAttribution;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 归因标注服务单元测试（工单 0139 S3）— 枚举校验、留痕、分布统计纯函数。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Case 归因标注服务测试")
class CaseAttributionServiceTest {

    @Mock
    private ICaseCandidateRepository caseCandidateRepository;

    @InjectMocks
    private CaseAttributionService service;

    @Test
    @DisplayName("标注留痕 — by/at 服务端生成，非法枚举拒绝")
    public void testLabelValidationAndAudit() {
        when(caseCandidateRepository.updateAttribution(anyLong(), any(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        IllegalArgumentException bad = assertThrows(IllegalArgumentException.class,
                () -> service.label(1L, "NOT_A_DIM", null, null));
        assertTrue(bad.getMessage().contains("非法归因"));

        assertEquals(1, service.label(1L, "TOOL", "参数组装错", "alice"));

        ArgumentCaptor<CaseAttribution> captor = ArgumentCaptor.forClass(CaseAttribution.class);
        verify(caseCandidateRepository).updateAttribution(eq(1L), captor.capture(), eq("参数组装错"), eq("alice"), anyString());
        assertEquals(CaseAttribution.TOOL, captor.getValue());
    }

    @Test
    @DisplayName("标注空 by 归一 unknown；候选不存在返回 0")
    public void testLabelEdgeCases() {
        when(caseCandidateRepository.updateAttribution(eq(2L), any(), any(), eq("unknown"), anyString()))
                .thenReturn(true);
        when(caseCandidateRepository.updateAttribution(eq(99L), any(), any(), anyString(), anyString()))
                .thenReturn(false);

        assertEquals(1, service.label(2L, "SKILL", null, "  "));
        assertEquals(0, service.label(99L, "PLANNING", null, "alice"));
    }

    @Test
    @DisplayName("分布统计纯函数 — 空集全零、单层、全层、占比和为 1")
    public void testSummarize() {
        // 空集：四层全 0
        Map<CaseAttribution, CaseAttributionService.AttributionStat> empty = service.summarize(List.of());
        assertEquals(4, empty.size());
        empty.values().forEach(s -> assertEquals(0, s.count));

        // 单层：1/1
        Map<CaseAttribution, CaseAttributionService.AttributionStat> single = service.summarize(List.of(
                CaseCandidateEntity.builder().attribution(CaseAttribution.ENVIRONMENT).build()
        ));
        assertEquals(1, single.get(CaseAttribution.ENVIRONMENT).count);
        assertEquals(1.0, single.get(CaseAttribution.ENVIRONMENT).ratio, 1e-9);
        assertEquals(0, single.get(CaseAttribution.PLANNING).count);

        // 全层占比
        Map<CaseAttribution, CaseAttributionService.AttributionStat> mixed = service.summarize(List.of(
                CaseCandidateEntity.builder().attribution(CaseAttribution.PLANNING).build(),
                CaseCandidateEntity.builder().attribution(CaseAttribution.PLANNING).build(),
                CaseCandidateEntity.builder().attribution(CaseAttribution.TOOL).build(),
                CaseCandidateEntity.builder().attribution(CaseAttribution.SKILL).build()
        ));
        assertEquals(2, mixed.get(CaseAttribution.PLANNING).count);
        assertEquals(0.5, mixed.get(CaseAttribution.PLANNING).ratio, 1e-9);
        assertEquals(0.25, mixed.get(CaseAttribution.TOOL).ratio, 1e-9);
        assertEquals(0.25, mixed.get(CaseAttribution.SKILL).ratio, 1e-9);
        assertEquals(0, mixed.get(CaseAttribution.ENVIRONMENT).count);
        double total = mixed.values().stream().mapToDouble(s -> s.ratio).sum();
        assertEquals(1.0, total, 1e-9);
    }

    @Test
    @DisplayName("loadAttributed — 时间窗与来源过滤透传仓储")
    public void testLoadAttributedFilter() {
        when(caseCandidateRepository.queryAttributed("2026-09-01 00:00:00", "2026-09-11 00:00:00",
                CaseSource.PATROL_FAIL, 2000)).thenReturn(List.of());

        service.loadAttributed("2026-09-01 00:00:00", "2026-09-11 00:00:00", CaseSource.PATROL_FAIL);
        service.loadAttributed(null, " ", null);

        verify(caseCandidateRepository).queryAttributed("2026-09-01 00:00:00", "2026-09-11 00:00:00",
                CaseSource.PATROL_FAIL, 2000);
        // 空白串归一在控制器层，服务层原样透传
        verify(caseCandidateRepository).queryAttributed(null, " ", null, 2000);
    }
}
