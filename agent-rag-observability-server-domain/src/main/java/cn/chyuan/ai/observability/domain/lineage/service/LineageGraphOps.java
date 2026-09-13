package cn.chyuan.ai.observability.domain.lineage.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 血缘图模型与遍历（工单 0286 AK2 + 0288 AK4 影响分析，借鉴 DataHub lineage）—
 * 表级上下游邻接 + 多跳 BFS（上游溯源 upstreamOf / 下游扩散 downstreamOf），
 * 深度上限防环防爆炸；影响分析区分直接/间接并给严重级启发（直接下游=HIGH）。
 * 纯函数，邻接由 LineageEdgeStore 供给。
 */
public final class LineageGraphOps {

    public static final int DEFAULT_MAX_DEPTH = 8;

    /** 血缘边 */
    public record LineageEdge(long id, String fromUrn, String toUrn, String source) {
    }

    /** 影响分析行 */
    public record ImpactRow(String urn, int depth, String severity) {
    }

    /** 影响分析报告 */
    public record ImpactReport(long id, String rootUrn, List<ImpactRow> rows, long analyzedAtMs) {

        public int highSeverity() {
            return (int) rows.stream().filter(r -> "HIGH".equals(r.severity())).count();
        }
    }

    private LineageGraphOps() {
    }

    /** 邻接表构建 */
    public static Map<String, Set<String>> adjacencyOf(List<LineageEdge> edges) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (LineageEdge edge : edges) {
            adjacency.computeIfAbsent(edge.fromUrn(), key -> new LinkedHashSet<>()).add(edge.toUrn());
        }
        return adjacency;
    }

    /** 多跳 BFS（direction=up 沿反向边 / down 沿正向边），返回 (节点, 跳数)，深度上限 */
    public static Map<String, Integer> traverse(List<LineageEdge> edges, String rootUrn,
            String direction, int maxDepth) {
        Map<String, Set<String>> forward = adjacencyOf(edges);
        Map<String, Set<String>> backward = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : forward.entrySet()) {
            for (String target : entry.getValue()) {
                backward.computeIfAbsent(target, key -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
        Map<String, Set<String>> steps = "up".equals(direction) ? backward : forward;
        Map<String, Integer> reached = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootUrn);
        reached.put(rootUrn, 0);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int depth = reached.get(current);
            if (depth >= maxDepth) {
                continue;
            }
            for (String next : steps.getOrDefault(current, Set.of())) {
                if (!reached.containsKey(next)) {
                    reached.put(next, depth + 1);
                    queue.add(next);
                }
            }
        }
        reached.remove(rootUrn);
        return reached;
    }

    /** 上游溯源（多跳） */
    public static Map<String, Integer> upstreamOf(List<LineageEdge> edges, String urn, int maxDepth) {
        return traverse(edges, urn, "up", maxDepth);
    }

    /** 下游扩散（多跳） */
    public static Map<String, Integer> downstreamOf(List<LineageEdge> edges, String urn, int maxDepth) {
        return traverse(edges, urn, "down", maxDepth);
    }

    /**
     * 影响分析：变更 rootUrn → 下游波及面（直接下游=HIGH，间接=LOW），
     * 按跳数升序排序；深度上限防爆炸。
     */
    public static List<ImpactRow> impactRows(List<LineageEdge> edges, String rootUrn, int maxDepth) {
        Map<String, Integer> impacted = downstreamOf(edges, rootUrn, maxDepth);
        List<ImpactRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : impacted.entrySet()) {
            rows.add(new ImpactRow(entry.getKey(), entry.getValue(),
                    entry.getValue() <= 1 ? "HIGH" : "LOW"));
        }
        rows.sort((a, b) -> a.depth() - b.depth() != 0 ? a.depth() - b.depth() : a.urn().compareTo(b.urn()));
        return rows;
    }
}
