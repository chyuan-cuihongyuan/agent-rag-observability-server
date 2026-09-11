package cn.chyuan.ai.observability.domain.mining.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.mining.adapter.repository.ICaseCandidateRepository;
import cn.chyuan.ai.observability.domain.mining.model.entity.CaseCandidateEntity;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseSource;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseStatus;
import cn.chyuan.ai.observability.domain.mining.service.CaseMiningService.CollectOutcome;
import cn.chyuan.ai.observability.domain.mining.service.CaseMiningService.MiningConfig;
import cn.chyuan.ai.observability.domain.mining.service.CaseMiningService.PromoteOutcome;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.patrol.adapter.repository.IPatrolRecordRepository;
import cn.chyuan.ai.observability.domain.patrol.model.entity.PatrolRecordEntity;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Case 挖掘服务单元测试（工单 0138 S2）— 三来源候选生成、幂等、回填与冻结不变式。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Case 挖掘服务测试")
class CaseMiningServiceTest {

    @Mock
    private IEvalResultRepository evalResultRepository;

    @Mock
    private IChatResultRepository chatResultRepository;

    @Mock
    private IPatrolRecordRepository patrolRecordRepository;

    @Mock
    private ICaseCandidateRepository caseCandidateRepository;

    @Mock
    private IEvalDatasetRepository evalDatasetRepository;

    private CaseMiningService service;

    @BeforeEach
    public void setUp() {
        service = new CaseMiningService(evalResultRepository, chatResultRepository,
                patrolRecordRepository, caseCandidateRepository, evalDatasetRepository);
    }

    @Test
    @DisplayName("三来源候选生成 — 字段齐、来源标记正确、幂等跳过已存在引用")
    public void testCollectThreeSources() {
        when(evalResultRepository.queryLowScore(anyDouble(), anyInt())).thenReturn(List.of(
                EvalResultEntity.builder().taskId("task-1").trialNo(1).traceId("t-a")
                        .queryText("低分查询").actualAnswer("低分答案").overallScore(0.32).build()
        ));
        when(chatResultRepository.queryByStatuses(anyList(), anyInt())).thenReturn(List.of(
                ChatResultEntity.builder().traceId("t-b").question("失败查询").answer(null).finalStatus("FAIL").build()
        ));
        when(patrolRecordRepository.queryLatestFailures(anyInt())).thenReturn(List.of(
                PatrolRecordEntity.builder().id(7L).traceId(null).query("巡检查询")
                        .status(PatrolStatus.TIMEOUT).errorSummary("拨测超时").build()
        ));
        // 只有低分来源的引用已存在（幂等命中）
        when(caseCandidateRepository.existsBySourceRef(CaseSource.EVAL_LOW_SCORE, "task-1:1:t-a")).thenReturn(true);
        when(caseCandidateRepository.existsBySourceRef(eq(CaseSource.TRACE_FAIL), anyString())).thenReturn(false);
        when(caseCandidateRepository.existsBySourceRef(CaseSource.PATROL_FAIL, "pid-7")).thenReturn(false);

        CollectOutcome outcome = service.collect(MiningConfig.of(0.6, 50));

        assertEquals(0, outcome.evalLowScore); // 幂等跳过
        assertEquals(1, outcome.traceFail);
        assertEquals(1, outcome.patrolFail);
        assertEquals(2, outcome.total());

        ArgumentCaptor<CaseCandidateEntity> captor = ArgumentCaptor.forClass(CaseCandidateEntity.class);
        verify(caseCandidateRepository, times(2)).insert(captor.capture());
        CaseCandidateEntity traceCase = captor.getAllValues().get(0);
        assertEquals(CaseSource.TRACE_FAIL, traceCase.getSource());
        assertEquals("t-b", traceCase.getSourceRef());
        assertEquals(CaseStatus.PENDING, traceCase.getStatus());
        CaseCandidateEntity patrolCase = captor.getAllValues().get(1);
        assertEquals(CaseSource.PATROL_FAIL, patrolCase.getSource());
        assertEquals("pid-7", patrolCase.getSourceRef()); // traceId 为空以 pid- 兜底幂等键
        assertTrue(patrolCase.getReason().contains("超时"));
    }

    @Test
    @DisplayName("单来源异常隔离 — 评测结果来源抛异常不阻塞其余来源")
    public void testSourceIsolation() {
        when(evalResultRepository.queryLowScore(anyDouble(), anyInt())).thenThrow(new RuntimeException("db down"));
        when(chatResultRepository.queryByStatuses(anyList(), anyInt())).thenReturn(List.of(
                ChatResultEntity.builder().traceId("t-c").question("q").finalStatus("TIMEOUT").build()
        ));
        when(caseCandidateRepository.existsBySourceRef(eq(CaseSource.TRACE_FAIL), anyString())).thenReturn(false);

        CollectOutcome outcome = service.collect(MiningConfig.of(0.6, 50));

        assertEquals(0, outcome.evalLowScore);
        assertEquals(1, outcome.traceFail);
        verify(caseCandidateRepository, times(1)).insert(any(CaseCandidateEntity.class));
    }

    @Test
    @DisplayName("回填错题集 — 新建错题本版本 1，条目 pool=wrong/source=trace/prompt 取候选查询/期望留空")
    public void testPromoteCreatesWrongDataset() {
        CaseCandidateEntity pending = CaseCandidateEntity.builder()
                .id(1L).source(CaseSource.TRACE_FAIL).sourceRef("t-1").traceId("t-1")
                .query("失败查询").status(CaseStatus.PENDING).build();
        when(caseCandidateRepository.queryByIds(List.of(1L))).thenReturn(List.of(pending));
        when(evalDatasetRepository.queryVersions("错题本")).thenReturn(List.of());
        when(caseCandidateRepository.updateStatus(anyList(), eq(CaseStatus.PROMOTED), any())).thenReturn(1);

        PromoteOutcome outcome = service.promote(List.of(1L), null);

        ArgumentCaptor<EvalDatasetEntity> dsCaptor = ArgumentCaptor.forClass(EvalDatasetEntity.class);
        verify(evalDatasetRepository).save(dsCaptor.capture());
        EvalDatasetEntity created = dsCaptor.getValue();
        assertEquals("错题本", created.getDatasetName());
        assertEquals("wrong", created.getPool());
        assertEquals("trace", created.getSource());
        assertEquals(1, created.getItemCount());
        var item = JSON.parseArray(created.getItemsJson(), cn.chyuan.ai.observability.domain.evaluate.model.valobj.EvalDatasetItem.class).get(0);
        assertEquals("失败查询", item.getPrompt());
        assertEquals("t-1", item.getTraceId());
        assertNull(item.getStandardAnswer()); // 期望行为留空待人工
        verify(caseCandidateRepository).updateStatus(List.of(1L), CaseStatus.PROMOTED, created.getDatasetId());
        assertEquals(1, outcome.promoted());
    }

    @Test
    @DisplayName("回填追加 — 既有未冻结错题本追加条目并更新计数；PENDING 过滤幂等")
    public void testPromoteAppendsAndFilters() {
        CaseCandidateEntity pending = CaseCandidateEntity.builder()
                .id(2L).source(CaseSource.PATROL_FAIL).sourceRef("pid-2").traceId("t-2")
                .query("巡检失败查询").status(CaseStatus.PENDING).build();
        CaseCandidateEntity promoted = CaseCandidateEntity.builder()
                .id(3L).source(CaseSource.TRACE_FAIL).sourceRef("t-3").status(CaseStatus.PROMOTED).build();
        when(caseCandidateRepository.queryByIds(List.of(2L, 3L))).thenReturn(List.of(pending, promoted));
        EvalDatasetEntity existing = EvalDatasetEntity.builder()
                .datasetId("ds-w1").datasetName("错题本").version(1).pool("wrong").source("trace")
                .frozen(false).itemCount(1).itemsJson("[{\"query\":\"旧条目\"}]").build();
        when(evalDatasetRepository.queryVersions("错题本")).thenReturn(List.of(existing));
        when(caseCandidateRepository.updateStatus(anyList(), eq(CaseStatus.PROMOTED), any())).thenReturn(1);

        PromoteOutcome outcome = service.promote(List.of(2L, 3L), "错题本");

        verify(evalDatasetRepository).update(existing);
        assertEquals(2, existing.getItemCount());
        assertTrue(existing.getItemsJson().contains("巡检失败查询"));
        verify(caseCandidateRepository).updateStatus(List.of(2L), CaseStatus.PROMOTED, "ds-w1");
        assertEquals(1, outcome.promoted());
    }

    @Test
    @DisplayName("冻结不变式 — 目标版本冻结时复制新版本追加，冻结版本不被修改")
    public void testPromoteFrozenCopiesNewVersion() {
        CaseCandidateEntity pending = CaseCandidateEntity.builder()
                .id(4L).source(CaseSource.TRACE_FAIL).sourceRef("t-4").query("新查询")
                .status(CaseStatus.PENDING).build();
        when(caseCandidateRepository.queryByIds(List.of(4L))).thenReturn(List.of(pending));
        EvalDatasetEntity frozen = EvalDatasetEntity.builder()
                .datasetId("ds-w2").datasetName("错题本").version(1).pool("wrong").source("trace")
                .frozen(true).itemCount(1).itemsJson("[{\"query\":\"旧条目\"}]").build();
        when(evalDatasetRepository.queryVersions("错题本")).thenReturn(List.of(frozen));
        when(evalDatasetRepository.maxVersion("错题本")).thenReturn(1);

        service.promote(List.of(4L), "错题本");

        // 冻结版本 v1 未被 update 修改：update 只作用于复制出的 v2 新版本（追加条目落库）
        verify(evalDatasetRepository).update(argThat(e -> e != null && Integer.valueOf(2).equals(e.getVersion())));
        verify(evalDatasetRepository, never()).update(argThat(e -> e != null && Integer.valueOf(1).equals(e.getVersion())));
        verify(caseCandidateRepository).updateStatus(List.of(4L), CaseStatus.PROMOTED, null); // mock 未回写 datasetId（仓储行为在实现层）
    }

    @Test
    @DisplayName("忽略 — 仅 PENDING 生效，空列表直接零")
    public void testIgnore() {
        when(caseCandidateRepository.updateStatus(List.of(5L, 6L), CaseStatus.IGNORED, null)).thenReturn(1);
        assertEquals(0, service.ignore(List.of()));
        assertEquals(1, service.ignore(List.of(5L, 6L)));
    }

    @Test
    @DisplayName("scan-limit 钳制 — 超上限收敛到 200、非正数回退 50")
    public void testMiningConfigClamp() {
        assertEquals(200, MiningConfig.of(0.6, 999).scanLimit());
        assertEquals(50, MiningConfig.of(0, 0).scanLimit());
        assertEquals(0.6, MiningConfig.of(0, 50).lowScoreThreshold());
    }
}
