package cn.chyuan.ai.observability.domain.tracekernel.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务依赖 DAG（工单 0974 EE7，jaeger 思想）。
 * 服务对调用计数/自环排除/边排序确定性。
 */
public final class DependencyDag {

    /** 一条依赖边：调用方 → 被调方 */
    public static final class Edge {
        public final String client;
        public final String server;
        public final long calls;

        Edge(String client, String server, long calls) {
            this.client = client;
            this.server = server;
            this.calls = calls;
        }
    }

    private final Map<String, Long> counts = new HashMap<>();

    /** 记一次调用：自环（client==server）排除 */
    public void record(String client, String server) {
        if (client.equals(server)) {
            return;
        }
        counts.merge(client + "->" + server, 1L, Long::sum);
    }

    /** 边列表：按 client 后 server 字典序确定性排序 */
    public List<Edge> edges() {
        List<Edge> out = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    String[] parts = e.getKey().split("->", -1);
                    out.add(new Edge(parts[0], parts[1], e.getValue()));
                });
        return out;
    }

    /** 指定服务对的计数 */
    public long calls(String client, String server) {
        return counts.getOrDefault(client + "->" + server, 0L);
    }

    public int size() {
        return counts.size();
    }

    /** 排序器暴露（测试辅助）：与 edges 同序 */
    static Comparator<Edge> order() {
        return Comparator.comparing((Edge e) -> e.client).thenComparing(e -> e.server);
    }
}
