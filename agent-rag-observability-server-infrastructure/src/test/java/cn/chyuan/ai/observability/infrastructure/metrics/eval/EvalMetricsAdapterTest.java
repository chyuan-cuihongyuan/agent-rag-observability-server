package cn.chyuan.ai.observability.infrastructure.metrics.eval;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * EvalMetricsAdapter 指标契约测试（SELFLOOP2 loop-204）。
 * 评测链路指标名（eval_*）与 null→unknown 的 tag 默认行为是对告警规则的契约。
 */
@DisplayName("EvalMetricsAdapter 指标命名契约")
class EvalMetricsAdapterTest {

    private SimpleMeterRegistry registry;
    private EvalMetricsAdapter adapter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        adapter = new EvalMetricsAdapter(registry);
    }

    @Test
    @DisplayName("recordTaskFinished → eval_task_finished 按 type/status 计数")
    void taskFinished_contract() {
        adapter.recordTaskFinished("retrieval", "success");
        adapter.recordTaskFinished("retrieval", "failed");

        assertEquals(1.0, registry.get("eval_task_finished")
                .tag("eval_type", "retrieval").tag("status", "success").counter().count());
        assertEquals(1.0, registry.get("eval_task_finished")
                .tag("eval_type", "retrieval").tag("status", "failed").counter().count());
    }

    @Test
    @DisplayName("recordTaskDuration → eval_task_duration_ms Timer 累计")
    void taskDuration_contract() {
        adapter.recordTaskDuration("retrieval", 300);

        assertEquals(1, registry.get("eval_task_duration_ms").tag("eval_type", "retrieval").timer().count());
        assertEquals(300.0, registry.get("eval_task_duration_ms").tag("eval_type", "retrieval").timer()
                .totalTime(TimeUnit.MILLISECONDS), 1e-9);
    }

    @Test
    @DisplayName("recordScore → eval_score Summary，null 分数记 0")
    void score_contract() {
        adapter.recordScore("retrieval", 0.87);
        adapter.recordScore("retrieval", null);

        assertEquals(0.87, registry.get("eval_score").tag("eval_type", "retrieval").summary().max(), 1e-9);
        assertEquals(2, registry.get("eval_score").tag("eval_type", "retrieval").summary().count());
    }

    @Test
    @DisplayName("recordHallucination → eval_hallucination_hit 计数")
    void hallucination_contract() {
        adapter.recordHallucination("faithfulness");
        assertEquals(1.0, registry.get("eval_hallucination_hit").tag("eval_type", "faithfulness").counter().count());
    }

    @Test
    @DisplayName("null/空 eval_type → unknown tag（label 稳定性契约）")
    void nullDefaultsToUnknown() {
        adapter.recordTaskFinished(null, "");
        adapter.recordTaskDuration("", 10);

        assertEquals(1.0, registry.get("eval_task_finished")
                .tag("eval_type", "unknown").tag("status", "unknown").counter().count());
        assertEquals(1, registry.get("eval_task_duration_ms").tag("eval_type", "unknown").timer().count());
        assertEquals(null, registry.find("eval_task_finished").tag("eval_type", "").counter());
    }
}
