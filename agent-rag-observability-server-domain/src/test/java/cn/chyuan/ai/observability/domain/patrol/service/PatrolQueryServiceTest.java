package cn.chyuan.ai.observability.domain.patrol.service;

import cn.chyuan.ai.observability.domain.patrol.adapter.repository.IPatrolRecordRepository;
import cn.chyuan.ai.observability.domain.patrol.model.entity.PatrolRecordEntity;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundSummary;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 巡检查询服务单元测试（工单 0137 S1）— 最近一轮汇总纯函数与分页钳制。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("巡检查询服务测试")
class PatrolQueryServiceTest {

    @Mock
    private IPatrolRecordRepository recordRepository;

    @InjectMocks
    private PatrolQueryService service;

    @Test
    @DisplayName("最近一轮汇总 — 空库返回 roundId=null 空汇总")
    public void testLatestRoundEmpty() {
        when(recordRepository.queryLatest()).thenReturn(null);

        PatrolRoundSummary summary = service.queryLatestRound();

        assertNull(summary.getRoundId());
        assertEquals(0, summary.getTotal());
        assertNull(summary.getAvgScore());
    }

    @Test
    @DisplayName("最近一轮汇总 — 同轮明细推导计数与平均分，取最新 create_time 为完成时间")
    public void testLatestRoundSummarize() {
        PatrolRecordEntity latest = PatrolRecordEntity.builder().roundId("P1").build();
        when(recordRepository.queryLatest()).thenReturn(latest);
        when(recordRepository.queryByRoundId("P1")).thenReturn(List.of(
                PatrolRecordEntity.builder().roundId("P1").status(PatrolStatus.SUCCESS).score(0.8).createTime("2026-09-11 10:00:00").build(),
                PatrolRecordEntity.builder().roundId("P1").status(PatrolStatus.SUCCESS).score(0.6).createTime("2026-09-11 10:00:01").build(),
                PatrolRecordEntity.builder().roundId("P1").status(PatrolStatus.FAIL).createTime("2026-09-11 10:00:02").build(),
                PatrolRecordEntity.builder().roundId("P1").status(PatrolStatus.TIMEOUT).createTime("2026-09-11 10:00:03").build()
        ));

        PatrolRoundSummary summary = service.queryLatestRound();

        assertEquals("P1", summary.getRoundId());
        assertEquals(4, summary.getTotal());
        assertEquals(2, summary.getSuccess());
        assertEquals(1, summary.getFail());
        assertEquals(1, summary.getTimeout());
        assertEquals(0.7, summary.getAvgScore(), 1e-9);
        assertEquals("2026-09-11 10:00:03", summary.getFinishedAt());
    }

    @Test
    @DisplayName("分页钳制 — page<1 归一、size 上限 100")
    public void testPaginationClamp() {
        when(recordRepository.queryList(anyInt(), anyInt())).thenReturn(List.of());

        service.queryRecords(0, 500);
        service.queryRecords(-3, 0);

        // 端口契约（page,size）：page<1 归一为 1、size 钳制 [1,100]（offset 换算在仓储层）
        verify(recordRepository).queryList(1, 100);
        verify(recordRepository).queryList(1, 1);
    }

    @Test
    @DisplayName("summarize 纯函数 — 未知状态从严按 FAIL 统计")
    public void testSummarizeUnknownStatusCountsAsFail() {
        PatrolRoundSummary summary = service.summarize("P2", List.of(
                PatrolRecordEntity.builder().roundId("P2").status(null).build()
        ));

        assertEquals(1, summary.getTotal());
        assertEquals(1, summary.getFail());
    }
}
