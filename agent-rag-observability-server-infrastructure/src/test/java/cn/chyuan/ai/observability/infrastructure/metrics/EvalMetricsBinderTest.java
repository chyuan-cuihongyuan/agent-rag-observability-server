package cn.chyuan.ai.observability.infrastructure.metrics;

import cn.chyuan.ai.observability.domain.evaluate.service.EvalConcurrencyGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * EvalMetricsBinder 绑定契约测试（SELFLOOP4 loop-407，工单 0612/0613）。
 * 真实 binder + SimpleMeterRegistry（@Resource 字段反射注入），断言四个水位点语义。
 */
@DisplayName("评测水位指标绑定")
class EvalMetricsBinderTest {

    private EvalMetricsBinder binderWith(EvalConcurrencyGuard guard, SimpleMeterRegistry registry)
            throws Exception {
        EvalMetricsBinder binder = new EvalMetricsBinder();
        set(binder, "meterRegistry", registry);
        set(binder, "evalConcurrencyGuard", guard);
        return binder;
    }

    private void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("活跃/剩余/上限/拒绝四点取值正确")
    void gaugesReflectGuardState() throws Exception {
        EvalConcurrencyGuard guard = new EvalConcurrencyGuard(2);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binderWith(guard, registry).bind();

        assertEquals(2.0, registry.get("observe_eval_permits_max").gauge().value());
        assertEquals(0.0, registry.get("observe_eval_active_tasks").gauge().value());

        guard.tryAcquire();
        assertEquals(1.0, registry.get("observe_eval_active_tasks").gauge().value());
        assertEquals(1.0, registry.get("observe_eval_permits_available").gauge().value());

        guard.tryAcquire();                        // 占满
        assertEquals(false, guard.tryAcquire());   // 饱和拒绝
        assertEquals(2.0, registry.get("observe_eval_active_tasks").gauge().value());
        assertEquals(1.0, registry.get("observe_eval_rejected_total").functionCounter().count());
    }

    @Test
    @DisplayName("下限钳位：max-concurrent-tasks=0 钳为 1")
    void zeroClampedToOne() throws Exception {
        EvalConcurrencyGuard guard = new EvalConcurrencyGuard(0);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binderWith(guard, registry).bind();
        assertEquals(1.0, registry.get("observe_eval_permits_max").gauge().value());
    }
}
