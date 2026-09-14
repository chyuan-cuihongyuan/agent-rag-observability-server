package cn.chyuan.ai.observability.infrastructure.es.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ES 慢查询计时（工单 0418/0419，SELFLOOP3 loop-310）
 */
class EsQueryTimerTest {

    @Test
    void thresholdBoundaryIsInclusive() {
        EsQueryTimer timer = new EsQueryTimer(100);
        assertThat(timer.isSlow(99)).isFalse();
        assertThat(timer.isSlow(100)).isTrue();
        assertThat(timer.isSlow(101)).isTrue();
    }

    @Test
    void supplierReturnValuePassesThrough() throws IOException {
        EsQueryTimer timer = new EsQueryTimer(1000);
        String result = timer.timed("test-query", () -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void exceptionPropagatesUnwrapped() {
        EsQueryTimer timer = new EsQueryTimer(1000);
        assertThatThrownBy(() -> timer.timed("failing", () -> {
            throw new IOException("es down");
        })).isInstanceOf(IOException.class).hasMessage("es down");
    }

    @Test
    void zeroOrNegativeThresholdClampsToOne() {
        assertThat(new EsQueryTimer(0).isSlow(1)).isTrue();
    }
}
