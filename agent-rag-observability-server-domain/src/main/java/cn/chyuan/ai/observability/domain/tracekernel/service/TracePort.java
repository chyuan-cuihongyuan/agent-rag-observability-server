package cn.chyuan.ai.observability.domain.tracekernel.service;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 分布式追踪端口（工单 0975 EE8，jaeger 思想）。
 * start·finish·query 入口统一编排/与 tskernel 样本序列作 long[] 形态只读联动（泛型数组不 import）/
 * trace-kernel.enabled 默认关（开启才改变行为）。
 */
public interface TracePort {

    /** 生成新上下文 */
    TraceContext generate();

    /** 解析 traceparent */
    TraceContext parse(String traceparent);

    /** 开 span */
    void start(String service, String name, String traceId, String spanId, String parentSpanId, long startMicros);

    /** 关 span */
    void finish(String spanId, long endMicros);

    /** 跨度视图 */
    record SpanView(String spanId, String traceId, String parentSpanId, String service, String name,
                    long startMicros, Long endMicros, Long durationMicros, boolean orphan) {
    }

    /** 查询：按服务/操作/最短时长过滤，按开始时间后 spanId 排序 */
    List<SpanView> query(String service, String operation, Long minDurationMicros);

    /** 悬挂窗步进 */
    void tick();

    /** 时钟偏斜校正 */
    void adjustSkew();

    /** tskernel 样本序列形态只读联动：span → [start, end, duration]（形状数据不 import tskernel） */
    long[] samplesOf(String spanId);

    static TracePort inMemory() {
        return new InMemoryTrace();
    }
}

final class InMemoryTrace implements TracePort {

    private final TraceStore store = new TraceStore(2);
    private final Random random = new Random(42);

    @Override
    public TraceContext generate() {
        return TraceContext.generate(random);
    }

    @Override
    public TraceContext parse(String traceparent) {
        return TraceContext.parse(traceparent);
    }

    @Override
    public void start(String service, String name, String traceId, String spanId, String parentSpanId, long startMicros) {
        store.start(traceId, spanId, parentSpanId, service, name, startMicros);
    }

    @Override
    public void finish(String spanId, long endMicros) {
        store.finish(spanId, endMicros);
    }

    @Override
    public List<TracePort.SpanView> query(String service, String operation, Long minDurationMicros) {
        return store.spans().stream()
                .filter(s -> service == null || s.service.equals(service))
                .filter(s -> operation == null || s.name.equals(operation))
                .filter(s -> {
                    if (minDurationMicros == null) {
                        return true;
                    }
                    return s.endMicros != null && s.endMicros - s.startMicros >= minDurationMicros;
                })
                .sorted(Comparator.comparingLong((TraceStore.Span s) -> s.startMicros)
                        .thenComparing(s -> s.spanId))
                .map(s -> new TracePort.SpanView(s.spanId, s.traceId, s.parentSpanId, s.service, s.name,
                        s.startMicros, s.endMicros,
                        s.endMicros == null ? null : s.endMicros - s.startMicros,
                        store.orphan(s)))
                .toList();
    }

    @Override
    public void tick() {
        store.tick();
    }

    @Override
    public void adjustSkew() {
        store.adjustSkew();
    }

    @Override
    public long[] samplesOf(String spanId) {
        TraceStore.Span span = store.get(spanId);
        if (span == null) {
            throw new IllegalArgumentException("未知 span: " + spanId);
        }
        if (span.endMicros == null) {
            throw new IllegalArgumentException("span 未关闭不可采样: " + spanId);
        }
        return new long[]{span.startMicros, span.endMicros, span.endMicros - span.startMicros};
    }

    TraceStore store() {
        return store;
    }
}
