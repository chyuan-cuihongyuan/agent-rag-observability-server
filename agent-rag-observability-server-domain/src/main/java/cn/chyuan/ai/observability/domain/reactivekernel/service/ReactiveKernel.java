package cn.chyuan.ai.observability.domain.reactivekernel.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 响应式内核（工单 0891-0897 DA1-DA7，vue 响应式思想）。
 * 键级 track/trigger 依赖收集/effect 注册与重跑/触发入队去重/computed 惰性缓存 dirty 传播/watch 新旧值/调度 flush 顺序/重跑前依赖清理。
 */
public final class ReactiveKernel {

    /** 响应式对象（DA1）：键级 get 收集依赖、set 触发 */
    public static final class ReactiveObject {
        private final ReactiveKernel runtime;
        private final Map<String, Object> values = new LinkedHashMap<>();

        ReactiveObject(ReactiveKernel runtime, Map<String, Object> initial) {
            values.putAll(initial);
            this.runtime = runtime;
        }

        public Object get(String prop) {
            runtime.track(depKey(this, prop));
            return values.get(prop);
        }

        public void set(String prop, Object value) {
            values.put(prop, value);
            runtime.trigger(depKey(this, prop));
        }

        static Object depKey(ReactiveObject obj, String prop) {
            return System.identityHashCode(obj) + "#" + prop;
        }
    }

    /** 副作用（DA2/DA7）：注册立即执行；触发时入队；重跑前清理旧依赖 */
    public static final class Effect implements Runnable {
        private final ReactiveKernel kernel;
        private final Runnable body;
        final Set<Object> deps = new HashSet<>();
        private boolean scheduled = false;
        private int runs = 0;
        private boolean stopped = false;

        Effect(ReactiveKernel kernel, Runnable body) {
            this.kernel = kernel;
            this.body = body;
        }

        @Override
        public void run() {
            cleanup();
            Effect prev = kernel.active;
            kernel.active = this;
            try {
                body.run();
                runs++;
            } finally {
                kernel.active = prev;
            }
        }

        /** 重跑前清理：从所有旧依赖订阅集摘除自己（分支切换失效口径） */
        void cleanup() {
            for (Object dep : deps) {
                Set<Effect> subs = kernel.subscribers.get(dep);
                if (subs != null) {
                    subs.remove(this);
                }
            }
            deps.clear();
        }

        void schedule() {
            if (stopped || scheduled) {
                return;
            }
            scheduled = true;
            kernel.queue.add(this);
        }

        void deliver() {
            scheduled = false;
            run();
        }

        public void stop() {
            stopped = true;
            cleanup();
        }

        public int runs() {
            return runs;
        }
    }

    /** computed（DA4）：惰性 + 缓存 + dirty 传播给订阅者 */
    public static final class Computed<T> {
        private final Supplier<T> getter;
        private final Object depId = new Object();
        private final ReactiveKernel kernel;
        private final Effect recompute;
        private T cached;
        private boolean dirty = true;
        private int computations = 0;

        Computed(ReactiveKernel kernel, Supplier<T> getter) {
            this.kernel = kernel;
            this.getter = getter;
            this.recompute = kernel.newEffect(() -> {
                cached = getter.get();
                computations++;
                kernel.trigger(depId);
            });
        }

        /** 读：向调用方注册订阅；dirty 时内联重算 */
        public T get() {
            kernel.track(depId);
            if (dirty) {
                dirty = false;
                recompute.run();
            }
            return cached;
        }

        public int computations() {
            return computations;
        }
    }

    /** watch 句柄（DA5） */
    public static final class WatchHandle {
        private final Effect effect;

        WatchHandle(Effect effect) {
            this.effect = effect;
        }

        public void stop() {
            effect.stop();
        }
    }

    final Map<Object, Set<Effect>> subscribers = new LinkedHashMap<>();
    final List<Effect> queue = new ArrayList<>();
    Effect active;
    private boolean flushing = false;

    /** 响应式对象工厂 */
    public ReactiveObject reactive(Map<String, Object> initial) {
        return new ReactiveObject(this, initial);
    }

    void track(Object depId) {
        if (active != null) {
            active.deps.add(depId);
            subscribers.computeIfAbsent(depId, k -> new LinkedHashSet<>()).add(active);
        }
    }

    void trigger(Object depId) {
        Set<Effect> subs = subscribers.get(depId);
        if (subs == null) {
            return;
        }
        for (Effect e : new ArrayList<>(subs)) {
            e.schedule();
        }
    }

    /** 创建 effect（不执行） */
    Effect newEffect(Runnable body) {
        return new Effect(this, body);
    }

    /** 注册并立即执行 effect（DA2） */
    public Effect effect(Runnable body) {
        Effect effect = newEffect(body);
        effect.run();
        return effect;
    }

    /** computed 工厂（DA4） */
    public <T> Computed<T> computed(Supplier<T> getter) {
        return new Computed<>(this, getter);
    }

    /** watch（DA5）：非 immediate 首轮只登记不回调；stop 停止（DA5） */
    public <T> WatchHandle watch(Supplier<T> source, BiConsumer<T, T> callback, boolean immediate) {
        Object[] last = {null};
        boolean[] first = {true};
        Effect effect = effect(() -> {
            T fresh = source.get();
            if (first[0] && !immediate) {
                first[0] = false;
                last[0] = fresh;
                return;
            }
            T old = (T) last[0];
            last[0] = fresh;
            first[0] = false;
            callback.accept(old, fresh);
        });
        return new WatchHandle(effect);
    }

    /** flush（DA3/DA6）：批量执行去重队列；执行中新调度在本轮续排；嵌套 flush 直接返回 */
    public void flush() {
        if (flushing) {
            return;
        }
        flushing = true;
        try {
            while (!queue.isEmpty()) {
                List<Effect> batch = new ArrayList<>(queue);
                queue.clear();
                for (Effect effect : batch) {
                    effect.deliver();
                }
            }
        } finally {
            flushing = false;
        }
    }

    public int pending() {
        return queue.size();
    }
}
