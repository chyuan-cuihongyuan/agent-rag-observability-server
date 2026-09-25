package cn.chyuan.ai.observability.domain.reactivekernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 响应式端口（工单 0898 DA8，vue 响应式思想）。
 * reactive·effect·computed·watch 入口统一编排/与 fiberkernel 任务入队形态只读联动（泛型队列不 import）/
 * reactive-kernel.enabled 默认关（开启才改变行为）。
 */
public interface ReactivePort {

    ReactiveKernel.ReactiveObject reactive(Map<String, Object> initial);

    ReactiveKernel.Effect effect(Runnable body);

    <T> ReactiveKernel.Computed<T> computed(Supplier<T> getter);

    <T> ReactiveKernel.WatchHandle watch(Supplier<T> source, java.util.function.BiConsumer<T, T> callback,
                                         boolean immediate);

    void flush();

    /** fiberkernel 只读联动形态：effect 调度转入宿主任务队列批量执行（队列形状不 import fiberkernel） */
    List<Runnable> scheduleInto(List<Runnable> sink);

    static ReactivePort inMemory() {
        return new InMemoryReactive();
    }
}

final class InMemoryReactive implements ReactivePort {

    private final ReactiveKernel kernel = new ReactiveKernel();
    private final List<ReactiveKernel.Effect> scheduled = new ArrayList<>();

    @Override
    public ReactiveKernel.ReactiveObject reactive(Map<String, Object> initial) {
        return kernel.reactive(initial);
    }

    @Override
    public ReactiveKernel.Effect effect(Runnable body) {
        ReactiveKernel.Effect effect = kernel.effect(body);
        scheduled.add(effect);
        return effect;
    }

    @Override
    public <T> ReactiveKernel.Computed<T> computed(Supplier<T> getter) {
        return kernel.computed(getter);
    }

    @Override
    public <T> ReactiveKernel.WatchHandle watch(java.util.function.Supplier<T> source,
                                                java.util.function.BiConsumer<T, T> callback, boolean immediate) {
        return kernel.watch(source, callback, immediate);
    }

    @Override
    public void flush() {
        kernel.flush();
    }

    @Override
    public List<Runnable> scheduleInto(List<Runnable> sink) {
        for (ReactiveKernel.Effect effect : scheduled) {
            sink.add(effect::run);
        }
        return sink;
    }
}
