package cn.chyuan.ai.observability.domain.reactivekernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 响应式内核测试（工单 0891-0898 DA1-DA8，vue 响应式思想）。
 * track/trigger/effect 重跑/去重批处理/computed 缓存传播/watch 新旧值/调度顺序/分支清理/端口编排。
 */
class ReactiveKernelTest {

    @Test
    void reactiveGetSetAndTrack() {
        ReactiveKernel kernel = new ReactiveKernel();
        ReactiveKernel.ReactiveObject state = kernel.reactive(Map.of("count", 1));
        assertEquals(1, state.get("count"));
        AtomicInteger reads = new AtomicInteger();
        ReactiveKernel.Effect effect = kernel.effect(() -> {
            state.get("count");
            reads.incrementAndGet();
        });
        assertEquals(1, effect.runs(), "注册立即执行");
        state.set("count", 2);
        assertEquals(1, kernel.pending(), "触发入队");
        kernel.flush();
        assertEquals(2, effect.runs(), "触发后重跑");
        assertEquals(2, reads.get());
    }

    @Test
    void triggerDedupeBeforeFlush() {
        ReactiveKernel kernel = new ReactiveKernel();
        ReactiveKernel.ReactiveObject state = kernel.reactive(Map.of("n", 0));
        ReactiveKernel.Effect effect = kernel.effect(() -> state.get("n"));
        state.set("n", 1);
        state.set("n", 2);
        state.set("n", 3);
        assertEquals(1, kernel.pending(), "同轮触发去重");
        kernel.flush();
        assertEquals(2, effect.runs(), "首跑+一轮一次");
    }

    @Test
    void computedLazyCacheAndPropagate() {
        ReactiveKernel kernel = new ReactiveKernel();
        ReactiveKernel.ReactiveObject state = kernel.reactive(Map.of("a", 2L, "b", 3L));
        ReactiveKernel.Computed<Long> sum = kernel.computed(() ->
                (Long) state.get("a") + (Long) state.get("b"));
        assertEquals(5L, sum.get());
        assertEquals(1, sum.computations(), "惰性首算");
        assertEquals(5L, sum.get());
        assertEquals(1, sum.computations(), "依赖未变缓存命中");
        state.set("a", 10L);
        kernel.flush();
        assertEquals(13L, sum.get());
        assertEquals(2, sum.computations(), "依赖变化重算");
        AtomicInteger observed = new AtomicInteger();
        ReactiveKernel.Effect reader = kernel.effect(() -> observed.addAndGet(sum.get().intValue()));
        assertEquals(13, observed.get(), "读取 computed 的 effect 建立依赖");
        state.set("b", 4L);
        kernel.flush();
        assertEquals(27, observed.get(), "computed 变化传播订阅 effect（13+14 累计）");
    }

    @Test
    void watchOldNewAndStop() {
        ReactiveKernel kernel = new ReactiveKernel();
        ReactiveKernel.ReactiveObject state = kernel.reactive(Map.of("v", 1));
        List<String> fired = new java.util.ArrayList<>();
        ReactiveKernel.WatchHandle handle = kernel.watch(() -> state.get("v"),
                (oldV, newV) -> fired.add(oldV + "->" + newV), false);
        assertTrue(fired.isEmpty(), "非 immediate 首轮不回调");
        state.set("v", 2);
        kernel.flush();
        assertEquals(List.of("1->2"), fired);
        state.set("v", 3);
        kernel.flush();
        assertEquals(List.of("1->2", "2->3"), fired);
        handle.stop();
        state.set("v", 4);
        kernel.flush();
        assertEquals(2, fired.size(), "停止后不再回调");
    }

    @Test
    void watchImmediate() {
        ReactiveKernel kernel = new ReactiveKernel();
        ReactiveKernel.ReactiveObject state = kernel.reactive(Map.of("v", 1));
        List<String> fired = new java.util.ArrayList<>();
        kernel.watch(() -> state.get("v"), (oldV, newV) -> fired.add(oldV + "->" + newV), true);
        assertEquals(List.of("null->1"), fired, "immediate 立即回调");
    }

    @Test
    void schedulerFlushOrderAndNesting() {
        ReactiveKernel kernel = new ReactiveKernel();
        ReactiveKernel.ReactiveObject a = kernel.reactive(Map.of("v", 0));
        ReactiveKernel.ReactiveObject b = kernel.reactive(Map.of("v", 0));
        List<String> order = new java.util.ArrayList<>();
        kernel.effect(() -> {
            order.add("A");
            a.get("v");
        });
        kernel.effect(() -> {
            order.add("B");
            b.get("v");
        });
        order.clear();
        a.set("v", 1);
        b.set("v", 1);
        kernel.flush();
        assertEquals(List.of("A", "B"), order, "flush 按调度顺序");
        ReactiveKernel k2 = new ReactiveKernel();
        ReactiveKernel.ReactiveObject go = k2.reactive(Map.of("go", 0));
        ReactiveKernel.ReactiveObject chain = k2.reactive(Map.of("v", 0));
        List<String> chainOrder = new java.util.ArrayList<>();
        k2.effect(() -> {
            go.get("go");
            chain.set("v", 1);
            chainOrder.add("first");
        });
        k2.effect(() -> {
            chain.get("v");
            chainOrder.add("second");
        });
        chainOrder.clear();
        go.set("go", 1);
        k2.flush();
        assertEquals(List.of("first", "second"), chainOrder, "flush 中新调度续排");
    }

    @Test
    void branchSwitchCleanup() {
        ReactiveKernel kernel = new ReactiveKernel();
        ReactiveKernel.ReactiveObject state = kernel.reactive(Map.of("useA", true, "a", "A1", "b", "B1"));
        List<String> seen = new java.util.ArrayList<>();
        kernel.effect(() -> {
            if (Boolean.TRUE.equals(state.get("useA"))) {
                seen.add(state.get("a").toString());
            } else {
                seen.add(state.get("b").toString());
            }
        });
        state.set("a", "A2");
        kernel.flush();
        assertTrue(seen.contains("A2"));
        state.set("useA", false);
        kernel.flush();
        state.set("a", "A3");
        int before = seen.size();
        kernel.flush();
        assertEquals(before, seen.size(), "分支切换后旧分支依赖失效");
        state.set("b", "B9");
        kernel.flush();
        assertTrue(seen.contains("B9"), "新分支依赖生效");
    }

    @Test
    void portOrchestrationAndQueueLinkage() {
        ReactivePort port = ReactivePort.inMemory();
        ReactiveKernel.ReactiveObject state = port.reactive(Map.of("n", 5L));
        ReactiveKernel.Computed<Long> doubled = port.computed(() -> (Long) state.get("n") * 2);
        assertEquals(10L, doubled.get());
        AtomicInteger observed = new AtomicInteger();
        port.effect(() -> observed.addAndGet(doubled.get().intValue()));
        state.set("n", 21L);
        port.flush();
        assertEquals(52, observed.get(), "effect/computed 统一编排（10+42 累计）");
        List<Runnable> sink = new java.util.ArrayList<>();
        port.scheduleInto(sink);
        assertEquals(1, sink.size(), "fiberkernel 任务入队形态只读联动");
        sink.get(0).run();
        assertEquals(94, observed.get(), "入队任务执行（52+42 累计）");
    }
}
