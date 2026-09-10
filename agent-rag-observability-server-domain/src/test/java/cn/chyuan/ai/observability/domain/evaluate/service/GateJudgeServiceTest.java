package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateRecordEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.TaskEvalSummary;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 门禁判定服务测试（工单 0136 R4 验收）— 任务完成回调判定与落账：
 * COMPLETED 判 PASS/BLOCK 落记录、FAILED（summary=null）按 BLOCK 落记录、
 * gate 缺失/停用跳过、落账异常不外抛（不回滚任务状态）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("门禁判定落账测试")
class GateJudgeServiceTest {

    @Mock
    private IGateRepository gateRepository;
    @Mock
    private IGateRecordRepository gateRecordRepository;
    @Spy
    private GateDecisionEngine decisionEngine = new GateDecisionEngine();

    @InjectMocks
    private GateJudgeService judgeService;

    private GateEntity enabledGate() {
        return GateEntity.builder()
                .gateId("gate-1").name("发布门禁").trials(3).enabled(true)
                .safetyDims(Map.of("hallucination", 0.8))
                .scoreThresholds(Map.of("overall", 0.6))
                .build();
    }

    private EvalTaskEntity task(String gateId) {
        return EvalTaskEntity.builder().taskId("task-1").evalType("ANSWER_QUALITY").gateId(gateId).build();
    }

    private TaskEvalSummary summary(double overall, double hallucination) {
        return TaskEvalSummary.builder()
                .taskId("task-1").trials(3).sampleCount(10)
                .avgOverall(overall).passRate(1.0).scoreStdDev(0.01)
                .dimensionAvg(Map.of("hallucination", hallucination))
                .build();
    }

    @Test
    @DisplayName("任务 COMPLETED + 全达标 — 落 PASS 记录（gateId/taskId/结论/时间齐全）")
    public void testJudgePass() {
        when(gateRepository.queryByGateId("gate-1")).thenReturn(enabledGate());

        judgeService.judgeAndRecord(task("gate-1"), summary(0.9, 0.95));

        ArgumentCaptor<GateRecordEntity> captor = ArgumentCaptor.forClass(GateRecordEntity.class);
        verify(gateRecordRepository).insert(captor.capture());
        GateRecordEntity record = captor.getValue();
        assertEquals("PASS", record.getResult());
        assertEquals("gate-1", record.getGateId());
        assertEquals("task-1", record.getTaskId());
        assertNotNull(record.getRecordId());
        assertNotNull(record.getCreateTime());
        assertEquals("[]", record.getTriggerDetail(), "PASS 无触发明细（空数组）");
    }

    @Test
    @DisplayName("任务 COMPLETED + 安全越限 — 落 BLOCK 记录（触发明细 JSON 可解析）")
    public void testJudgeBlockWithDetail() {
        when(gateRepository.queryByGateId("gate-1")).thenReturn(enabledGate());

        // hallucination 安全分 0.5 < 0.8 一票否决（overall 0.9 达标也不放行）
        judgeService.judgeAndRecord(task("gate-1"), summary(0.9, 0.5));

        ArgumentCaptor<GateRecordEntity> captor = ArgumentCaptor.forClass(GateRecordEntity.class);
        verify(gateRecordRepository).insert(captor.capture());
        GateRecordEntity record = captor.getValue();
        assertEquals("BLOCK", record.getResult());
        List<GateDecisionEngine.GateTrigger> triggers =
                JSON.parseObject(record.getTriggerDetail(), new TypeReference<List<GateDecisionEngine.GateTrigger>>() {});
        assertEquals(1, triggers.size());
        assertEquals("SAFETY", triggers.get(0).ruleType());
        assertEquals("hallucination", triggers.get(0).dim());
    }

    @Test
    @DisplayName("任务 FAILED（summary=null）— 按 BLOCK 落记录（防回测挂掉静默放行）")
    public void testJudgeTaskFailed() {
        when(gateRepository.queryByGateId("gate-1")).thenReturn(enabledGate());

        judgeService.judgeAndRecord(task("gate-1"), null);

        ArgumentCaptor<GateRecordEntity> captor = ArgumentCaptor.forClass(GateRecordEntity.class);
        verify(gateRecordRepository).insert(captor.capture());
        assertEquals("BLOCK", captor.getValue().getResult());
        assertTrue(captor.getValue().getTriggerDetail().contains("TASK_FAILED"));
    }

    @Test
    @DisplayName("gate 缺失或已停用 — 跳过判定不落记录（停用等价解绑）")
    public void testSkipWhenGateMissingOrDisabled() {
        when(gateRepository.queryByGateId("g-none")).thenReturn(null);
        judgeService.judgeAndRecord(task("g-none"), summary(0.9, 0.9));
        verify(gateRecordRepository, never()).insert(any());

        GateEntity disabled = enabledGate();
        disabled.setEnabled(false);
        when(gateRepository.queryByGateId("gate-1")).thenReturn(disabled);
        judgeService.judgeAndRecord(task("gate-1"), summary(0.9, 0.9));
        verify(gateRecordRepository, never()).insert(any());
    }

    @Test
    @DisplayName("非回测任务（gateId 空）— 直接返回不判定")
    public void testSkipWhenNoGateBinding() {
        judgeService.judgeAndRecord(task(null), summary(0.9, 0.9));
        judgeService.judgeAndRecord(task("  "), summary(0.9, 0.9));
        judgeService.judgeAndRecord(null, summary(0.9, 0.9));
        verifyNoInteractions(gateRepository);
        verifyNoInteractions(gateRecordRepository);
    }

    @Test
    @DisplayName("判定/落账异常 — 吞掉不外抛（不回滚任务状态），记录缺失即「未判定」")
    public void testJudgeExceptionSwallowed() {
        when(gateRepository.queryByGateId("gate-1")).thenReturn(enabledGate());
        doThrow(new RuntimeException("db down")).when(gateRecordRepository).insert(any());

        assertDoesNotThrow(() -> judgeService.judgeAndRecord(task("gate-1"), summary(0.9, 0.9)));
    }
}
