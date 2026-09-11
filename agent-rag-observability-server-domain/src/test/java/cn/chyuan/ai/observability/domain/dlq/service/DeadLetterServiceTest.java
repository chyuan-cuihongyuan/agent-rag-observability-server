package cn.chyuan.ai.observability.domain.dlq.service;

import cn.chyuan.ai.observability.domain.dlq.adapter.repository.IDeadLetterRepository;
import cn.chyuan.ai.observability.domain.dlq.model.entity.DeadLetterEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 死信服务单元测试（工单 0180 Y4）— 记录、重放成功删除、重放失败计数、异常不阻断。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("死信记录与重放服务测试")
class DeadLetterServiceTest {

    @Mock
    private IDeadLetterRepository deadLetterRepository;

    @Mock
    private DeadLetterService.DeadLetterHandler deadLetterHandler;

    @InjectMocks
    private DeadLetterService service;

    @Test
    @DisplayName("记录 — 字段齐、payload 截断 8KB、落库异常不抛出")
    public void testRecord() {
        service.record("chat_result", "{\"big\": \"" + "x".repeat(9000) + "\"}", "parse error");

        verify(deadLetterRepository).insert(any(DeadLetterEntity.class));

        // 落库异常不阻断消费链
        doThrow(new RuntimeException("db down")).when(deadLetterRepository).insert(any());
        assertDoesNotThrow(() -> service.record("chat_result", "p", "e"));
    }

    @Test
    @DisplayName("重放成功 — handle true 删除记录")
    public void testReplaySuccess() {
        DeadLetterEntity entity = DeadLetterEntity.builder().id(1L).topicTag("chat_result").payload("p").build();
        when(deadLetterRepository.queryById(1L)).thenReturn(entity);
        when(deadLetterHandler.handle("chat_result", "p")).thenReturn(true);

        Map<String, Object> result = service.replay(1L);

        assertEquals(true, result.get("replayed"));
        verify(deadLetterRepository).delete(1L);
    }

    @Test
    @DisplayName("重放失败 — handle false 或异常均 retry_count+1 留痕")
    public void testReplayFailure() {
        DeadLetterEntity entity = DeadLetterEntity.builder().id(2L).topicTag("chat_result").payload("p").build();
        when(deadLetterRepository.queryById(2L)).thenReturn(entity);
        when(deadLetterHandler.handle("chat_result", "p")).thenReturn(false);

        Map<String, Object> fail = service.replay(2L);
        assertEquals(false, fail.get("replayed"));
        verify(deadLetterRepository).markRetryFailed(2L, "handle 返回失败");

        when(deadLetterRepository.queryById(3L)).thenReturn(
                DeadLetterEntity.builder().id(3L).topicTag("chat_result").payload("p2").build());
        when(deadLetterHandler.handle("chat_result", "p2")).thenThrow(new IllegalStateException("boom"));

        Map<String, Object> error = service.replay(3L);
        assertEquals(false, error.get("replayed"));
        verify(deadLetterRepository).markRetryFailed(3L, "boom");
    }

    @Test
    @DisplayName("重放不存在的记录 — 返回错误信息不抛出")
    public void testReplayMissing() {
        when(deadLetterRepository.queryById(99L)).thenReturn(null);

        Map<String, Object> result = service.replay(99L);

        assertEquals(false, result.get("replayed"));
        verify(deadLetterRepository, never()).delete(anyLong());
    }
}
