package cn.chyuan.ai.observability.domain.patrol.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IAnswerSourceProvider;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.AnswerSample;
import cn.chyuan.ai.observability.domain.observe.service.TraceQualityCalculator;
import cn.chyuan.ai.observability.domain.patrol.adapter.port.IPatrolMetricsPort;
import cn.chyuan.ai.observability.domain.patrol.adapter.repository.IPatrolRecordRepository;
import cn.chyuan.ai.observability.domain.patrol.model.entity.PatrolRecordEntity;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundConfig;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundSummary;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 巡检拨测领域服务单元测试（工单 0137 S1）—
 * 三态判定（成功/失败/超时）、种子解析两来源、单种子异常隔离、汇总统计。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("巡检拨测服务测试")
class PatrolProbeServiceTest {

    @Mock
    private IAnswerSourceProvider answerSourceProvider;

    @Mock
    private IPatrolRecordRepository recordRepository;

    @Mock
    private IPatrolMetricsPort metricsPort;

    @Mock
    private IEvalDatasetRepository evalDatasetRepository;

    private PatrolProbeService service;

    @BeforeEach
    public void setUp() {
        // TraceQualityCalculator 为纯启发式实现，直接用真实实例（评分逻辑已在自身测试覆盖）
        service = new PatrolProbeService(answerSourceProvider, new TraceQualityCalculator(),
                recordRepository, metricsPort, evalDatasetRepository);
    }

    private PatrolRoundConfig config(List<String> queries) {
        return PatrolRoundConfig.builder()
                .timeoutMs(800)
                .agentId("agent-1")
                .queries(queries)
                .datasetId(null)
                .build();
    }

    @Test
    @DisplayName("成功拨测 — SUCCESS 记录含轻量分与 traceId，汇总计数正确")
    public void testSuccessRound() {
        when(answerSourceProvider.fetch(eq("你好"), eq("agent-1")))
                .thenReturn(AnswerSample.builder()
                        .traceId("t-1")
                        .actualAnswer("你好，这是一条足够长的回答内容用于评分。")
                        .retrievedChunks(List.of("片段一"))
                        .build());

        PatrolRoundSummary summary = service.runRound(config(List.of("你好")));

        assertEquals(1, summary.getTotal());
        assertEquals(1, summary.getSuccess());
        assertEquals(0, summary.getFail());
        assertEquals(0, summary.getTimeout());
        assertNotNull(summary.getAvgScore());

        ArgumentCaptor<PatrolRecordEntity> captor = ArgumentCaptor.forClass(PatrolRecordEntity.class);
        verify(recordRepository).insert(captor.capture());
        PatrolRecordEntity record = captor.getValue();
        assertEquals(PatrolStatus.SUCCESS, record.getStatus());
        assertEquals("t-1", record.getTraceId());
        assertNotNull(record.getScore());
        assertTrue(record.getDurationMs() >= 0);
        assertNotNull(record.getRoundId());
        assertEquals("q-1", record.getTaskRef());
        verify(metricsPort).recordProbeFinished(eq(PatrolStatus.SUCCESS), anyLong(), any());
    }

    @Test
    @DisplayName("失败拨测 — provider 返回 null 记 FAIL，错误摘要非空")
    public void testFailRoundWhenProviderReturnsNull() {
        when(answerSourceProvider.fetch(anyString(), anyString())).thenReturn(null);

        PatrolRoundSummary summary = service.runRound(config(List.of("问题")));

        assertEquals(1, summary.getFail());
        assertEquals(0, summary.getSuccess());
        assertEquals(0, summary.getTimeout());
        assertNull(summary.getAvgScore());

        ArgumentCaptor<PatrolRecordEntity> captor = ArgumentCaptor.forClass(PatrolRecordEntity.class);
        verify(recordRepository).insert(captor.capture());
        assertEquals(PatrolStatus.FAIL, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getErrorSummary());
        verify(metricsPort).recordProbeFinished(eq(PatrolStatus.FAIL), anyLong(), isNull());
    }

    @Test
    @DisplayName("超时拨测 — future 超时记 TIMEOUT，耗时记录为超时预算值，且主动取消底层调用")
    public void testTimeoutRound() {
        when(answerSourceProvider.fetch(anyString(), anyString())).thenAnswer(inv -> {
            // 模拟慢上游：阻塞超过预算（800ms）
            TimeUnit.MILLISECONDS.sleep(2500);
            return AnswerSample.builder().traceId("t-slow").actualAnswer("晚到的答案").build();
        });

        long start = System.currentTimeMillis();
        PatrolRoundSummary summary = service.runRound(config(List.of("慢问题")));
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(1, summary.getTimeout());
        assertEquals(0, summary.getSuccess());
        // 轮次应在预算附近返回，而不是等满底层调用（2500ms）
        assertTrue(elapsed < 2000, "拨测应在超时预算附近返回, elapsed=" + elapsed);

        ArgumentCaptor<PatrolRecordEntity> captor = ArgumentCaptor.forClass(PatrolRecordEntity.class);
        verify(recordRepository).insert(captor.capture());
        PatrolRecordEntity record = captor.getValue();
        assertEquals(PatrolStatus.TIMEOUT, record.getStatus());
        assertEquals(800L, record.getDurationMs());
        assertTrue(record.getErrorSummary().contains("超时"));
        verify(metricsPort).recordProbeFinished(eq(PatrolStatus.TIMEOUT), eq(800L), isNull());
    }

    @Test
    @DisplayName("种子异常隔离 — 单种子抛异常记 FAIL，不阻塞同轮后续种子")
    public void testPerSeedIsolation() {
        when(answerSourceProvider.fetch(eq("爆"), anyString())).thenThrow(new RuntimeException("boom"));
        when(answerSourceProvider.fetch(eq("好"), anyString()))
                .thenReturn(AnswerSample.builder().traceId("t-2").actualAnswer("正常回答内容足够长").build());

        PatrolRoundSummary summary = service.runRound(config(List.of("爆", "好")));

        assertEquals(2, summary.getTotal());
        assertEquals(1, summary.getFail());
        assertEquals(1, summary.getSuccess());
        verify(recordRepository, times(2)).insert(any(PatrolRecordEntity.class));
    }

    @Test
    @DisplayName("golden 池种子 — queries 为空时回退数据集条目，prompt 优先于 query")
    public void testGoldenDatasetSeeds() {
        when(evalDatasetRepository.queryByDatasetId("ds-golden")).thenReturn(EvalDatasetEntity.builder()
                .datasetId("ds-golden")
                .itemsJson("[{\"prompt\":\"三元组提示词\",\"query\":\"兜底查询\"},{\"query\":\"第二条查询\"}]")
                .build());

        PatrolRoundSummary summary = service.runRound(PatrolRoundConfig.builder()
                .timeoutMs(800).queries(List.of()).datasetId("ds-golden").build());

        assertEquals(2, summary.getTotal());
        verify(answerSourceProvider).fetch(eq("三元组提示词"), isNull());
        verify(answerSourceProvider).fetch(eq("第二条查询"), isNull());
    }

    @Test
    @DisplayName("空种子轮 — total=0 空汇总，不调用 provider 不落库")
    public void testEmptySeedsRound() {
        PatrolRoundSummary summary = service.runRound(PatrolRoundConfig.builder()
                .timeoutMs(800).queries(List.of()).datasetId(null).build());

        assertEquals(0, summary.getTotal());
        verifyNoInteractions(answerSourceProvider);
        verifyNoInteractions(recordRepository);
    }

    @Test
    @DisplayName("多状态混合轮 — 汇总三分计数与平均分只统计成功样本")
    public void testMixedRoundSummary() {
        when(answerSourceProvider.fetch(eq("ok"), anyString()))
                .thenReturn(AnswerSample.builder().traceId("t-3").actualAnswer("成功回答，长度充分").build());
        when(answerSourceProvider.fetch(eq("bad"), anyString())).thenReturn(null);
        when(answerSourceProvider.fetch(eq("slow"), anyString())).thenAnswer(inv -> {
            TimeUnit.MILLISECONDS.sleep(1200);
            return AnswerSample.builder().actualAnswer("慢").build();
        });

        PatrolRoundSummary summary = service.runRound(config(List.of("ok", "bad", "slow")));

        assertEquals(3, summary.getTotal());
        assertEquals(1, summary.getSuccess());
        assertEquals(1, summary.getFail());
        assertEquals(1, summary.getTimeout());
        assertNotNull(summary.getAvgScore());
    }
}
