package cn.chyuan.ai.observability.domain.pipekernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 拓扑声明（工单 0976 EF1 / 0977 EF2，vector 思想）。
 * sources/transforms/sinks 三节解析格式错误拒绝/引用闭合/孤儿节点拒绝/环拒绝。
 */
public final class Topology {

    /** 拓扑节点：source / transform / sink */
    public static final class Node {
        public final String id;
        public final String kind;
        public final String type;
        public final List<String> inputs;

        Node(String id, String kind, String type, List<String> inputs) {
            this.id = id;
            this.kind = kind;
            this.type = type;
            this.inputs = inputs;
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();

    /** 解析 DSL：三节声明，行格式 id = type[, inputs=a,b] */
    public static Topology parse(String text) {
        Topology topology = new Topology();
        String section = null;
        int lineNo = 0;
        for (String raw : text.split("\n")) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[")) {
                if (!line.endsWith("]")) {
                    throw new IllegalArgumentException("节名格式非法(行 " + lineNo + "): " + line);
                }
                section = line.substring(1, line.length() - 1);
                if (!List.of("source", "transform", "sink").contains(section)) {
                    throw new IllegalArgumentException("未知节(行 " + lineNo + "): " + section);
                }
                continue;
            }
            if (section == null) {
                throw new IllegalArgumentException("节外声明行(行 " + lineNo + "): " + line);
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("缺少 =(行 " + lineNo + "): " + line);
            }
            String id = line.substring(0, eq).trim();
            String rest = line.substring(eq + 1).trim();
            if (id.isEmpty() || rest.isEmpty()) {
                throw new IllegalArgumentException("空 id 或类型(行 " + lineNo + "): " + line);
            }
            if (topology.nodes.containsKey(id)) {
                throw new IllegalArgumentException("重复节点(行 " + lineNo + "): " + id);
            }
            String[] parts = rest.split(",");
            String type = parts[0].trim();
            List<String> inputs = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (!part.startsWith("inputs=")) {
                    throw new IllegalArgumentException("未知属性(行 " + lineNo + "): " + part);
                }
                for (String input : part.substring("inputs=".length()).split("\\+")) {
                    String ref = input.trim();
                    if (ref.isEmpty()) {
                        throw new IllegalArgumentException("空引用(行 " + lineNo + ")");
                    }
                    inputs.add(ref);
                }
            }
            if (section.equals("source") && !inputs.isEmpty()) {
                throw new IllegalArgumentException("source 不接受输入(行 " + lineNo + "): " + id);
            }
            topology.nodes.put(id, new Node(id, section, type, List.copyOf(inputs)));
        }
        if (topology.nodes.isEmpty()) {
            throw new IllegalArgumentException("拓扑为空");
        }
        return topology;
    }

    /** 闭合校验：输入引用已定义、无输入中游拒绝、孤儿（无消费路径）拒绝、环拒绝 */
    public void validate() {
        for (Node node : nodes.values()) {
            if (!node.kind.equals("source") && node.inputs.isEmpty()) {
                throw new IllegalArgumentException("无输入节点: " + node.id);
            }
            for (String input : node.inputs) {
                if (!nodes.containsKey(input)) {
                    throw new IllegalArgumentException("引用未定义: " + node.id + " -> " + input);
                }
            }
        }
        Set<String> consumed = new LinkedHashSet<>();
        for (Node node : nodes.values()) {
            consumed.addAll(node.inputs);
        }
        for (Node node : nodes.values()) {
            if (!node.kind.equals("sink") && !consumed.contains(node.id)) {
                throw new IllegalArgumentException("孤儿节点: " + node.id);
            }
        }
        for (Node node : nodes.values()) {
            if (hasCycle(node.id, new LinkedHashSet<>())) {
                throw new IllegalArgumentException("环拒绝: " + node.id);
            }
        }
    }

    /** 拓扑序节点（source 起，输入先于输出） */
    public List<Node> ordered() {
        List<Node> out = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        for (Node node : nodes.values()) {
            visit(node, done, out);
        }
        return out;
    }

    private void visit(Node node, Set<String> done, List<Node> out) {
        if (done.contains(node.id)) {
            return;
        }
        for (String input : node.inputs) {
            visit(nodes.get(input), done, out);
        }
        done.add(node.id);
        out.add(node);
    }

    private boolean hasCycle(String id, Set<String> path) {
        if (!path.add(id)) {
            return true;
        }
        Node node = nodes.get(id);
        for (String input : node.inputs) {
            if (hasCycle(input, path)) {
                return true;
            }
        }
        path.remove(id);
        return false;
    }

    public Node get(String id) {
        return nodes.get(id);
    }

    public List<Node> all() {
        return List.copyOf(nodes.values());
    }
}
