package cn.chyuan.ai.observability.domain.pipekernel.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 拓扑管道端口（工单 0983 EF8，vector 思想）。
 * load·pump 入口统一编排/与 logkernel 记录作字符串形态只读联动（泛型文本不 import）/
 * pipe-kernel.enabled 默认关（开启才改变行为）。
 */
public interface PipePort {

    /** 加载拓扑并校验闭合 */
    void load(String configText);

    /** 注册变换程序（transform id → VRL 语句） */
    void program(String transformId, List<String> statements);

    /** 为 sink 登记路由模板 */
    void routeSink(String sinkId, String template);

    /** 泵送一条事件：返回序号；变换丢弃返回 -1；未知来源/缓冲满/跨型/模板缺变量拒绝 */
    long pump(String sourceId, Event event);

    /** sink 确认 */
    void ack(long seq);

    /** 水位：最小连续已确认 */
    long watermark();

    /** 暂存（未确认）事件数：至少一次不丢 */
    int pending();

    /** 车道内容 */
    List<Event> lane(String key);

    /** logkernel 记录形态只读联动：事件 → 记录串（形状数据不 import logkernel） */
    static String recordOf(Event event) {
        if (Event.LOG.equals(event.type())) {
            return "log://" + event.field("message");
        }
        return "metric://" + event.field("name");
    }

    static PipePort inMemory() {
        return new InMemoryPipe();
    }
}

final class InMemoryPipe implements PipePort {

    private Topology topology;
    private final Map<String, Vrl> programs = new LinkedHashMap<>();
    private final AckWatermark watermark = new AckWatermark();
    private final Router router = new Router();
    private final Deque<Pending> pendingList = new ArrayDeque<>();
    private final int capacity = 8;
    private long seq = 0;

    private static final class Pending {
        final long seq;
        final Event event;

        Pending(long seq, Event event) {
            this.seq = seq;
            this.event = event;
        }
    }

    @Override
    public void load(String configText) {
        Topology parsed = Topology.parse(configText);
        parsed.validate();
        this.topology = parsed;
    }

    @Override
    public void program(String transformId, List<String> statements) {
        programs.put(transformId, Vrl.compile(statements));
    }

    @Override
    public void routeSink(String sinkId, String template) {
        router.route(sinkId, template);
    }

    @Override
    public long pump(String sourceId, Event event) {
        if (topology == null) {
            throw new IllegalStateException("拓扑未加载");
        }
        Topology.Node source = topology.get(sourceId);
        if (source == null || !source.kind.equals("source")) {
            throw new IllegalArgumentException("未知来源: " + sourceId);
        }
        if (pendingList.size() >= capacity) {
            throw new IllegalStateException("缓冲已满拒绝");
        }
        Event current = event;
        for (Topology.Node node : topology.ordered()) {
            if (!node.kind.equals("transform")) {
                continue;
            }
            Vrl vrl = programs.get(node.id);
            if (vrl != null) {
                current = vrl.apply(current);
                if (current == null) {
                    return -1;
                }
            }
        }
        long assigned = ++seq;
        boolean delivered = false;
        for (Topology.Node node : topology.ordered()) {
            if (!node.kind.equals("sink")) {
                continue;
            }
            if (node.type.equals("log_console")) {
                current.requireLog();
            }
            if (node.type.equals("metric_sink")) {
                current.requireMetric();
            }
            router.dispatch(node.id, current);
            delivered = true;
        }
        if (!delivered) {
            throw new IllegalStateException("无可用 sink");
        }
        pendingList.addLast(new Pending(assigned, current));
        return assigned;
    }

    @Override
    public void ack(long seq) {
        boolean removed = pendingList.removeIf(p -> p.seq == seq);
        if (!removed) {
            throw new IllegalArgumentException("未知待确认序号: " + seq);
        }
        watermark.ack(seq);
    }

    @Override
    public long watermark() {
        return watermark.watermark();
    }

    @Override
    public int pending() {
        return pendingList.size();
    }

    @Override
    public List<Event> lane(String key) {
        return router.lane(key);
    }
}
