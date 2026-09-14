package cn.chyuan.ai.observability.domain.evaluate.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评测并发闸门 — 许可边界（工单 0404/0405，SELFLOOP3 loop-303）
 */
class EvalConcurrencyGuardTest {

    @Test
    void thirdAcquireFailsUntilRelease() {
        EvalConcurrencyGuard guard = new EvalConcurrencyGuard(2);
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();

        guard.release();
        assertThat(guard.tryAcquire()).isTrue();
    }

    @Test
    void zeroOrNegativeConfigClampsToOne() {
        EvalConcurrencyGuard guard = new EvalConcurrencyGuard(0);
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();
    }
}
