package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
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
import static org.mockito.Mockito.*;

/**
 * 回测服务测试（工单 0136 R4 验收）— 触发入口：
 * datasetId/pool 两种取数、trials 取门禁配置、gateId 绑定任务、立即返回 taskId（异步执行不等待）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("回测触发服务测试")
class BacktestServiceTest {

    @Mock
    private IGateRepository gateRepository;
    @Mock
    private IEvalDatasetRepository evalDatasetRepository;
    @Mock
    private EvaluateService evaluateService;

    @InjectMocks
    private BacktestService backtestService;

    private GateEntity gate(boolean enabled) {
        return GateEntity.builder()
                .gateId("gate-1").name("发布门禁").trials(3).enabled(enabled)
                .safetyDims(Map.of("hallucination", 0.8))
                .scoreThresholds(Map.of("overall", 0.6))
                .build();
    }

    private EvalDatasetEntity dataset(String datasetId) {
        return EvalDatasetEntity.builder()
                .datasetId(datasetId).datasetName("黄金集").version(2).pool("golden")
                .itemsJson("[{\"query\":\"q1\",\"prompt\":\"P\",\"promptVersion\":\"v1\",\"traceId\":\"t-1\"}]")
                .build();
    }

    @Test
    @DisplayName("datasetId 模式 — 任务创建带 trials=gate 配置与 gateId 绑定，并触发异步执行")
    public void testTriggerByDatasetId() {
        when(gateRepository.queryByGateId("gate-1")).thenReturn(gate(true));
        when(evalDatasetRepository.queryByDatasetId("ds-1")).thenReturn(dataset("ds-1"));
        when(evaluateService.createTask(any(EvalTaskEntity.class))).thenReturn("task-001");

        Map<String, String> result = backtestService.triggerBacktest(
                "ds-1", null, "gate-1", null, "model-v2", "rag-v3");

        assertEquals("task-001", result.get("taskId"));
        assertEquals("gate-1", result.get("gateId"));
        assertEquals("ds-1", result.get("datasetId"));

        ArgumentCaptor<EvalTaskEntity> captor = ArgumentCaptor.forClass(EvalTaskEntity.class);
        verify(evaluateService).createTask(captor.capture());
        EvalTaskEntity task = captor.getValue();
        assertEquals(3, task.getTrials(), "trials 取门禁配置（Pass@k 执行链）");
        assertEquals("gate-1", task.getGateId(), "gateId 绑定任务（完成后回调门禁判定）");
        assertEquals("ds-1", task.getDatasetId());
        assertEquals("ANSWER_QUALITY", task.getEvalType(), "evalType 缺省 ANSWER_QUALITY");
        assertEquals("model-v2", task.getModelVersion());
        assertEquals("rag-v3", task.getRagStrategyVersion());
        assertTrue(task.getTaskName().contains("发布门禁"), "任务名携带门禁名");
        // 立即返回：调用 runTask 触发异步执行（@Async 由 Spring 代理，此处验证调用发生）
        verify(evaluateService).runTask("task-001");
    }

    @Test
    @DisplayName("pool 模式 — 取该池最新一条数据集；trials null 时兜底 1")
    public void testTriggerByPool() {
        GateEntity g = gate(true);
        g.setTrials(null);
        when(gateRepository.queryByGateId("gate-1")).thenReturn(g);
        when(evalDatasetRepository.queryByPool("golden", 1, 1)).thenReturn(List.of(dataset("ds-9")));
        when(evaluateService.createTask(any(EvalTaskEntity.class))).thenReturn("task-009");

        Map<String, String> result = backtestService.triggerBacktest(
                null, "golden", "gate-1", "RAG_RETRIEVAL", null, null);

        assertEquals("task-009", result.get("taskId"));
        assertEquals("ds-9", result.get("datasetId"));
        ArgumentCaptor<EvalTaskEntity> captor = ArgumentCaptor.forClass(EvalTaskEntity.class);
        verify(evaluateService).createTask(captor.capture());
        assertEquals(1, captor.getValue().getTrials(), "gate.trials null 兜底 1");
        assertEquals("RAG_RETRIEVAL", captor.getValue().getEvalType(), "evalType 显式指定生效");
    }

    @Test
    @DisplayName("datasetId 优先于 pool（同时提供时）")
    public void testDatasetIdTakesPrecedence() {
        when(gateRepository.queryByGateId("gate-1")).thenReturn(gate(true));
        when(evalDatasetRepository.queryByDatasetId("ds-1")).thenReturn(dataset("ds-1"));
        when(evaluateService.createTask(any(EvalTaskEntity.class))).thenReturn("task-001");

        backtestService.triggerBacktest("ds-1", "golden", "gate-1", null, null, null);

        verify(evalDatasetRepository, never()).queryByPool(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("入参校验 — gate 不存在/停用、datasetId 与 pool 缺一、数据集不存在均结构化拒绝")
    public void testValidation() {
        // gate 不存在
        when(gateRepository.queryByGateId("g-none")).thenReturn(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> backtestService.triggerBacktest("ds-1", null, "g-none", null, null, null));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(evaluateService, never()).createTask(any());

        // gate 停用
        when(gateRepository.queryByGateId("gate-1")).thenReturn(gate(false));
        ex = assertThrows(IllegalArgumentException.class,
                () -> backtestService.triggerBacktest("ds-1", null, "gate-1", null, null, null));
        assertTrue(ex.getMessage().contains("停用"));

        // datasetId 与 pool 都缺
        when(gateRepository.queryByGateId("gate-1")).thenReturn(gate(true));
        ex = assertThrows(IllegalArgumentException.class,
                () -> backtestService.triggerBacktest(null, " ", "gate-1", null, null, null));
        assertTrue(ex.getMessage().contains("必须提供其一"));

        // datasetId 指向的数据集不存在
        when(evalDatasetRepository.queryByDatasetId("ds-none")).thenReturn(null);
        ex = assertThrows(IllegalArgumentException.class,
                () -> backtestService.triggerBacktest("ds-none", null, "gate-1", null, null, null));
        assertTrue(ex.getMessage().contains("数据集不存在"));

        // 池内无数据集
        when(evalDatasetRepository.queryByPool("golden", 1, 1)).thenReturn(List.of());
        ex = assertThrows(IllegalArgumentException.class,
                () -> backtestService.triggerBacktest(null, "golden", "gate-1", null, null, null));
        assertTrue(ex.getMessage().contains("样本池内无数据集"));
    }
}
