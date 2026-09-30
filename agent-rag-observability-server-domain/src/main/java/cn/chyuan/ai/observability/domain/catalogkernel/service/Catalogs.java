package cn.chyuan.ai.observability.domain.catalogkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 注册目录（工单 1090 ES1，consul 思想）。
 * 节点+服务两级注册/注销节点级联摘除服务/重复注册幂等/同节点多服务并存。
 */
public final class Catalogs {

    private final Map<String, Set<String>> byNode = new LinkedHashMap<>();

    /** 注册：节点+服务；重复幂等返回 false */
    public boolean register(String node, String service) {
        requireNode(node);
        requireService(service);
        return byNode.computeIfAbsent(node, key -> new LinkedHashSet<>()).add(service);
    }

    /** 注销节点：级联摘除其全部服务，返回摘除数；未知节点拒绝 */
    public int deregisterNode(String node) {
        Set<String> services = byNode.remove(node);
        if (services == null) {
            throw new IllegalArgumentException("未知节点拒绝注销: " + node);
        }
        return services.size();
    }

    /** 注销单服务：未知拒绝 */
    public void deregisterService(String node, String service) {
        Set<String> services = byNode.get(node);
        if (services == null || !services.remove(service)) {
            throw new IllegalArgumentException("未知服务拒绝注销: " + node + "/" + service);
        }
    }

    /** 提供服务的节点列表 */
    public List<String> services(String service) {
        List<String> nodes = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : byNode.entrySet()) {
            if (entry.getValue().contains(service)) {
                nodes.add(entry.getKey());
            }
        }
        return nodes;
    }

    public Set<String> nodeServices(String node) {
        return Set.copyOf(byNode.getOrDefault(node, Set.of()));
    }

    public List<String> nodes() {
        return new ArrayList<>(byNode.keySet());
    }

    /** 目录快照：node/service 对列表 */
    public List<String> snapshot() {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : byNode.entrySet()) {
            for (String service : entry.getValue()) {
                pairs.add(entry.getKey() + "/" + service);
            }
        }
        return pairs;
    }

    private void requireNode(String node) {
        if (node == null || node.isBlank()) {
            throw new IllegalArgumentException("节点不能为空");
        }
    }

    private void requireService(String service) {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("服务不能为空");
        }
    }
}
