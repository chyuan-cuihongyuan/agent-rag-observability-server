package cn.chyuan.ai.observability.infrastructure.adapter.llm;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmJudgePort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IJudgeCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 带缓存 judge 装饰器单元测试（工单 0176 X7）— 命中省调用、未命中回写、缓存故障降级。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("带缓存 judge 装饰器测试")
class CachedLlmJudgePortTest {

    @Mock
    private ILlmJudgePort delegate;

    @Mock
    private IJudgeCacheRepository cacheRepository;

    @Test
    @DisplayName("命中 — 不调底层、hit+1、返回缓存输出")
    public void testHit() {
        when(cacheRepository.lookup(anyString())).thenReturn("verdict: 1");
        CachedLlmJudgePort port = new CachedLlmJudgePort(delegate, cacheRepository);

        String output = port.complete("  评判 prompt  ");

        assertEquals("verdict: 1", output);
        verify(delegate, never()).complete(anyString());
        verify(cacheRepository).incrementHit(anyString());
    }

    @Test
    @DisplayName("未命中 — 调底层并回写（trim 后同键）")
    public void testMissThenStore() {
        when(cacheRepository.lookup(anyString())).thenReturn(null);
        when(delegate.complete(anyString())).thenReturn("verdict: 0");
        CachedLlmJudgePort port = new CachedLlmJudgePort(delegate, cacheRepository);

        String output = port.complete("prompt-A");

        assertEquals("verdict: 0", output);
        verify(cacheRepository).insert(anyString(), eq("verdict: 0"), isNull());
        verify(cacheRepository, never()).incrementHit(anyString());
    }

    @Test
    @DisplayName("缓存故障降级 — 读失败直调底层，写失败忽略不抛")
    public void testCacheFailureDegradation() {
        when(cacheRepository.lookup(anyString())).thenThrow(new RuntimeException("db down"));
        when(delegate.complete(anyString())).thenReturn("ok");
        org.mockito.Mockito.doThrow(new RuntimeException("write down"))
                .when(cacheRepository).insert(anyString(), anyString(), any());
        CachedLlmJudgePort port = new CachedLlmJudgePort(delegate, cacheRepository);

        assertEquals("ok", port.complete("prompt-B"));
        verify(delegate).complete(anyString());
    }

    @Test
    @DisplayName("键归一 — trim 等价输入同键")
    public void testKeyNormalization() {
        assertEquals(
                cn.chyuan.ai.observability.domain.evaluate.service.JudgeCacheKeys.cacheKey("abc"),
                cn.chyuan.ai.observability.domain.evaluate.service.JudgeCacheKeys.cacheKey("  abc  "));
        assertNotEquals(
                cn.chyuan.ai.observability.domain.evaluate.service.JudgeCacheKeys.cacheKey("abc"),
                cn.chyuan.ai.observability.domain.evaluate.service.JudgeCacheKeys.cacheKey("abd"));
        assertThrows(IllegalArgumentException.class,
                () -> cn.chyuan.ai.observability.domain.evaluate.service.JudgeCacheKeys.cacheKey(null));
    }
}
