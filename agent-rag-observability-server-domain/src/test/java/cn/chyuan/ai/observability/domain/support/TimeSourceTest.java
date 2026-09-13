package cn.chyuan.ai.observability.domain.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单调时间源契约（SELFLOOP2 loop-238）。
 */
@DisplayName("TimeSource 单调耗时契约")
class TimeSourceTest {

    @Test
    @DisplayName("立即取值非负")
    void immediateNonNegative() {
        assertTrue(TimeSource.started().elapsedMillis() >= 0);
    }

    @Test
    @DisplayName("sleep 15ms 后 elapsed ≥ 15（单调不回拨）")
    void monotonicAfterSleep() throws InterruptedException {
        TimeSource.Started timer = TimeSource.started();
        Thread.sleep(15);
        long elapsed = timer.elapsedMillis();
        assertTrue(elapsed >= 15, "elapsed 应 ≥ 15，实际 " + elapsed);
        assertTrue(elapsed < 5000, "elapsed 异常大，疑似挂钟混用: " + elapsed);
    }

    @Test
    @DisplayName("多次 elapsed 单调不减")
    void monotonicNonDecreasing() {
        TimeSource.Started timer = TimeSource.started();
        long a = timer.elapsedMillis();
        long b = timer.elapsedMillis();
        assertTrue(b >= a);
    }
}
