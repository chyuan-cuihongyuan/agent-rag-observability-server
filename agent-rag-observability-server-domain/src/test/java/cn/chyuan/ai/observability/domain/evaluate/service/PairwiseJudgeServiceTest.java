package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmPairwisePort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IPairwiseRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.PairwiseOutcome;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * pairwise 对比判定服务单元测试（工单 0170 X1）— 规则兜底、LLM 判定与异常回退、汇总统计。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("pairwise 对比判定服务测试")
class PairwiseJudgeServiceTest {

    @Mock
    private IEvalTaskRepository evalTaskRepository;

    @Mock
    private IEvalResultRepository evalResultRepository;

    @Mock
    private IEvalDatasetRepository evalDatasetRepository;

    @Mock
    private ILlmPairwisePort llmPairwisePort;

    @Mock
    private IPairwiseRecordRepository pairwiseRecordRepository;

    @InjectMocks
    private PairwiseJudgeService service;

    private EvalResultEntity result(String query, String answer, Double overall) {
        return EvalResultEntity.builder().queryText(query).actualAnswer(answer).overallScore(overall).build();
    }

    private void mockTasks() {
        when(evalTaskRepository.queryByTaskId("taskA")).thenReturn(EvalTaskEntity.builder().taskId("taskA").build());
        when(evalTaskRepository.queryByTaskId("taskB")).thenReturn(EvalTaskEntity.builder().taskId("taskB").build());
        when(evalResultRepository.queryByTaskId(eq("taskA"), eq(1), eq(1), anyInt())).thenReturn(List.of(
                result("q1", "a-A", 0.9),
                result("q2", "b-A", 0.4),
                result("q3", "c-A", 0.7)
        ));
        when(evalResultRepository.queryByTaskId(eq("taskB"), eq(1), eq(1), anyInt())).thenReturn(List.of(
                result("q1", "a-B", 0.5),
                result("q2", "b-B", 0.6)
        ));
    }

    @Test
    @DisplayName("规则兜底纯函数 — 高分胜/相等与 null 均 TIE")
    public void testRuleFallback() {
        assertEquals(PairwiseOutcome.A_WIN, PairwiseJudgeService.ruleFallback(0.9, 0.5));
        assertEquals(PairwiseOutcome.B_WIN, PairwiseJudgeService.ruleFallback(0.3, 0.5));
        assertEquals(PairwiseOutcome.TIE, PairwiseJudgeService.ruleFallback(0.5, 0.5));
        assertEquals(PairwiseOutcome.TIE, PairwiseJudgeService.ruleFallback(null, 0.5));
    }

    @Test
    @DisplayName("LLM 判定正常路径 — 逐题判定落库与汇总统计")
    public void testCompareWithLlm() {
        mockTasks();
        when(llmPairwisePort.judge(anyString(), anyString(), anyString()))
                .thenReturn(PairwiseOutcome.A_WIN, PairwiseOutcome.B_WIN);

        Map<String, Object> summary = service.compare("taskA", "taskB", "ds-1");

        assertEquals(2, summary.get("pairs"));
        assertEquals(1, summary.get("aWins"));
        assertEquals(1, summary.get("bWins"));
        assertEquals(0.5, (Double) summary.get("winRateA"), 1e-9);
        verify(pairwiseRecordRepository, times(2)).insert(eq("taskA"), eq("taskB"), eq("ds-1"),
                anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("LLM 返回 null / 抛异常 — 回退 overallScore 规则")
    public void testFallbackOnLlmFailure() {
        mockTasks();
        when(llmPairwisePort.judge(anyString(), anyString(), anyString()))
                .thenReturn(null) // q1: 规则 0.9>0.5 → A_WIN
                .thenThrow(new RuntimeException("llm down")); // q2: 规则 0.4<0.6 → B_WIN

        Map<String, Object> summary = service.compare("taskA", "taskB", null);

        assertEquals(2, summary.get("pairs"));
        assertEquals(1, summary.get("aWins"));
        assertEquals(1, summary.get("bWins"));
    }

    @Test
    @DisplayName("任务不存在拒绝")
    public void testTaskNotFound() {
        when(evalTaskRepository.queryByTaskId("nope")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.compare("nope", "taskB", null));
        verifyNoInteractions(pairwiseRecordRepository);
    }
}
