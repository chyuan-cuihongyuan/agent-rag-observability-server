package cn.chyuan.ai.observability.infrastructure.adapter.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JudgeRetrySupport 钳位单测（SELFLOOP4 loop-413，工单 0624/0625）。
 * spring-ai 2.0.1 底层为 OpenAI 官方 SDK：重试语义由 maxRetries 承担，此处只验钳位。
 */
class JudgeRetrySupportTest {

    @Test
    @DisplayName("钳位 [0,10]：负数归 0、超大截 10、合法值原样")
    void clampedToValidRange() {
        assertEquals(0, JudgeRetrySupport.clampMaxRetries(-1));
        assertEquals(0, JudgeRetrySupport.clampMaxRetries(0));
        assertEquals(3, JudgeRetrySupport.clampMaxRetries(3));
        assertEquals(10, JudgeRetrySupport.clampMaxRetries(99));
    }
}
