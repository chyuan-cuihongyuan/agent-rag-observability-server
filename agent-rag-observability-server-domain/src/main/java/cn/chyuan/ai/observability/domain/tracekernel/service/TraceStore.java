package cn.chyuan.ai.observability.domain.tracekernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨度存储（工单 0969 EE2 / 0970 EE3 / 0971 EE4 / 0973 EE6，jaeger 思想）。
 * span 开闭落时长未关拒绝统计/span 树 children 组装缺父补根多根排序/时钟偏斜校正不改真实序/悬挂回填超窗判孤儿。
 */
public final class TraceStore {

    /** 一条跨度记录 */
    public static final class Span {
        public final String traceId;
        public final String spanId;
        public final String parentSpanId;
        public final String service;
        public final String name;
        public long startMicros;
        public Long endMicros;
        public long originalStartMicros;
        public long skewAdjustMicros;
        public int parkedTicks = 0;

        Span(String traceId, String spanId, String parentSpanId, String service, String name, long startMicros) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.service = service;
            this.name = name;
            this.startMicros = startMicros;
            this.originalStartMicros = startMicros;
        }
    }

    /** 树节点：children 按 start 后 id 排序，根按 name 排序 */
    public static final class Node {
        public final Span span;
        public final List<Node> children = new ArrayList<>();

        Node(Span span) {
            this.span = span;
        }
    }

    private final Map<String, Span> spans = new LinkedHashMap<>();
    private final int parkWindowTicks;

    public TraceStore(int parkWindowTicks) {
        if (parkWindowTicks <= 0) {
            throw new IllegalArgumentException("悬挂窗口非法: " + parkWindowTicks);
        }
        this.parkWindowTicks = parkWindowTicks;
    }

    /** 开 span：spanId 重复拒绝 */
    public Span start(String traceId, String spanId, String parentSpanId, String service, String name, long startMicros) {
        if (spans.containsKey(spanId)) {
            throw new IllegalArgumentException("重复 spanId: " + spanId);
        }
        Span span = new Span(traceId, spanId, parentSpanId, service, name, startMicros);
        spans.put(spanId, span);
        return span;
    }

    /** 关 span：未知/重复关闭/结束早于开始拒绝 */
    public void finish(String spanId, long endMicros) {
        Span span = spans.get(spanId);
        if (span == null) {
            throw new IllegalArgumentException("未知 span: " + spanId);
        }
        if (span.endMicros != null) {
            throw new IllegalArgumentException("重复关闭: " + spanId);
        }
        if (endMicros < span.startMicros) {
            throw new IllegalArgumentException("结束早于开始: " + spanId);
        }
        span.endMicros = endMicros;
    }

    /** 时长：未关闭拒绝统计 */
    public long duration(String spanId) {
        Span span = spans.get(spanId);
        if (span == null) {
            throw new IllegalArgumentException("未知 span: " + spanId);
        }
        if (span.endMicros == null) {
            throw new IllegalArgumentException("span 未关闭不可统计: " + spanId);
        }
        return span.endMicros - span.startMicros;
    }

    public List<Span> spans() {
        return List.copyOf(spans.values());
    }

    public Span get(String spanId) {
        return spans.get(spanId);
    }

    /** 悬挂状态：父缺失且未超窗 */
    public boolean parked(Span span) {
        return span.parentSpanId != null && !spans.containsKey(span.parentSpanId)
                && span.parkedTicks < parkWindowTicks;
    }

    /** 孤儿状态：父缺失且已超窗 */
    public boolean orphan(Span span) {
        return span.parentSpanId != null && !spans.containsKey(span.parentSpanId)
                && span.parkedTicks >= parkWindowTicks;
    }

    /** 时钟步进：父缺失 span 悬挂计数 +1（父到达后自动回填） */
    public void tick() {
        for (Span span : spans.values()) {
            if (span.parentSpanId != null && !spans.containsKey(span.parentSpanId)) {
                span.parkedTicks++;
            }
        }
    }

    /** 时钟偏斜校正：子开始不得早于父开始，前移并记录校正量（真实序保留 originalStart） */
    public void adjustSkew() {
        for (Span span : spans.values()) {
            if (span.parentSpanId == null) {
                continue;
            }
            Span parent = spans.get(span.parentSpanId);
            if (parent != null && span.startMicros < parent.startMicros) {
                long shift = parent.startMicros - span.startMicros;
                span.startMicros += shift;
                span.skewAdjustMicros = shift;
            }
        }
    }

    /** span 树：children 按 parent 组装；缺父（悬挂/孤儿）补为根；根按 name 排序，子按 start 后 spanId 排序 */
    public List<Node> buildTree() {
        List<Node> roots = new ArrayList<>();
        Map<String, Node> byId = new LinkedHashMap<>();
        for (Span span : spans.values()) {
            byId.put(span.spanId, new Node(span));
        }
        for (Span span : spans.values()) {
            Node node = byId.get(span.spanId);
            Span parent = span.parentSpanId == null ? null : spans.get(span.parentSpanId);
            if (parent == null) {
                roots.add(node);
            } else {
                parentChildren(byId.get(parent.spanId)).add(node);
            }
        }
        for (Node node : byId.values()) {
            node.children.sort((a, b) -> {
                int byStart = Long.compare(a.span.startMicros, b.span.startMicros);
                return byStart != 0 ? byStart : a.span.spanId.compareTo(b.span.spanId);
            });
        }
        roots.sort((a, b) -> {
            int byName = a.span.name.compareTo(b.span.name);
            return byName != 0 ? byName : a.span.spanId.compareTo(b.span.spanId);
        });
        return roots;
    }

    private List<Node> parentChildren(Node parent) {
        return parent.children;
    }
}
