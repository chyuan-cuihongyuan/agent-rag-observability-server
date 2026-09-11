package cn.chyuan.ai.observability.domain.insight.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 保留清理服务单元测试（工单 0152 U6）— 批次编排、dry-run 不删、单表异常隔离。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("保留清理服务测试")
class RetentionServiceTest {

    @Mock
    private RetentionService.RetentionExecutor executor;

    private RetentionService serviceWithBatchSize(int batchSize) {
        return new RetentionService(executor, batchSize);
    }

    @Test
    @DisplayName("批次编排 — 满批继续、不足批停止；三表都清理")
    public void testPurgeBatching() {
        RetentionService service = serviceWithBatchSize(100);
        // chat_result_log：第一批满批、第二批 30 条停止；其余表第一批就完
        when(executor.purgeBatch(eq("chat_result_log"), anyString(), eq(100))).thenReturn(100, 30, 0);
        when(executor.purgeBatch(eq("agent_decision_log"), anyString(), eq(100))).thenReturn(5);
        when(executor.purgeBatch(eq("rag_retrieval_log"), anyString(), eq(100))).thenReturn(0);

        LocalDateTime before = LocalDateTime.of(2026, 9, 1, 0, 0);
        Map<String, Long> result = service.purge(before);

        assertEquals(130L, result.get("chat_result_log"));
        assertEquals(5L, result.get("agent_decision_log"));
        assertEquals(0L, result.get("rag_retrieval_log"));
        // 满批后应再拉一批（100→30 停止），共 2 次
        verify(executor, times(2)).purgeBatch(eq("chat_result_log"), anyString(), eq(100));
    }

    @Test
    @DisplayName("单表异常隔离 — 第一张表抛异常，其余表继续")
    public void testTableIsolation() {
        RetentionService service = serviceWithBatchSize(10);
        when(executor.purgeBatch(eq("chat_result_log"), anyString(), anyInt()))
                .thenThrow(new RuntimeException("db down"));
        when(executor.purgeBatch(eq("agent_decision_log"), anyString(), anyInt())).thenReturn(3);
        when(executor.purgeBatch(eq("rag_retrieval_log"), anyString(), anyInt())).thenReturn(4);

        Map<String, Long> result = service.purge(LocalDateTime.now().minusDays(90));

        assertEquals(0L, result.get("chat_result_log"));
        assertEquals(3L, result.get("agent_decision_log"));
        assertEquals(4L, result.get("rag_retrieval_log"));
    }

    @Test
    @DisplayName("dry-run — 只统计不删除")
    public void testDryRun() {
        RetentionService service = serviceWithBatchSize(500);
        when(executor.countPurge(anyString(), anyString())).thenReturn(42L);

        Map<String, Long> result = service.dryRun(LocalDateTime.now().minusDays(90));

        assertEquals(3, result.size());
        assertEquals(42L, result.get("chat_result_log"));
        verify(executor, never()).purgeBatch(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("表白名单 — 固定三张日志表，评测资产不在清理范围")
    public void testTableWhitelist() {
        assertEquals(3, RetentionService.PURGEABLE_TABLES.size());
        assertTrue(RetentionService.PURGEABLE_TABLES.contains("chat_result_log"));
        assertFalse(RetentionService.PURGEABLE_TABLES.contains("eval_dataset"));
        assertFalse(RetentionService.PURGEABLE_TABLES.contains("trace_annotation"));
    }
}
