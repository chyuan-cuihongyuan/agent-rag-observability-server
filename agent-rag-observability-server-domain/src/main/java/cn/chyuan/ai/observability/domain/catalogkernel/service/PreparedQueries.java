package cn.chyuan.ai.observability.domain.catalogkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * prepared query（工单 1093 ES4，consul 思想）。
 * 匹配服务就近有序选择/调用失败重选下一/全部不健康整体失败/过滤条件不匹配返回空。
 */
public final class PreparedQueries {

    /** 查询定义：名字 + 服务 + 标签过滤（空串不过滤） */
    public record Query(String name, String service, String tagFilter) {
    }

    /** 候选实例：节点 + 标签集 + 是否健康 */
    public record Candidate(String node, List<String> tags, boolean healthy) {
    }

    private final Map<String, Query> queries = new LinkedHashMap<>();

    /** 定义查询：重名拒绝 */
    public void define(String name, String service, String tagFilter) {
        if (name == null || name.isBlank() || service == null || service.isBlank()) {
            throw new IllegalArgumentException("查询要素不能为空");
        }
        if (queries.containsKey(name)) {
            throw new IllegalArgumentException("查询重名拒绝: " + name);
        }
        queries.put(name, new Query(name, service, tagFilter == null ? "" : tagFilter));
    }

    /** 解析：按候选序（就近）返回健康且标签匹配的节点；无匹配返回空 */
    public List<String> resolve(String name, List<Candidate> candidates) {
        Query query = requireQuery(name);
        List<String> nodes = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!candidate.healthy()) {
                continue;
            }
            if (!query.tagFilter().isEmpty() && !candidate.tags().contains(query.tagFilter())) {
                continue;
            }
            nodes.add(candidate.node());
        }
        return nodes;
    }

    /** 执行：就近依次尝试，失败重选下一；空解析返回空、全部失败抛异常 */
    public List<String> execute(String name, List<Candidate> candidates, Try tryFn) {
        List<String> resolved = resolve(name, candidates);
        List<String> tried = new ArrayList<>();
        for (String node : resolved) {
            tried.add(node);
            if (tryFn.tryCall(node)) {
                return tried;
            }
        }
        if (resolved.isEmpty()) {
            return tried;
        }
        throw new IllegalStateException("prepared query 全部失败: " + name + " " + tried);
    }

    /** 尝试面 */
    public interface Try {
        boolean tryCall(String node);
    }

    public Query query(String name) {
        return requireQuery(name);
    }

    private Query requireQuery(String name) {
        Query query = queries.get(name);
        if (query == null) {
            throw new IllegalArgumentException("未定义查询拒绝: " + name);
        }
        return query;
    }
}
