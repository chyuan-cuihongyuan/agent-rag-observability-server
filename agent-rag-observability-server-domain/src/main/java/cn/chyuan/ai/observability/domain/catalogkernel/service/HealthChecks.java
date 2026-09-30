package cn.chyuan.ai.observability.domain.catalogkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康聚合（工单 1091 ES2，consul 思想）。
 * 检查 passing·warning·critical 三态/服务聚合取最差/无检查视为 passing/节点下线全检查 critical。
 */
public final class HealthChecks {

    /** 检查三态（严重度升序） */
    public enum Status { PASSING, WARNING, CRITICAL }

    private final Map<String, Status> checks = new LinkedHashMap<>();

    /** 上报检查：键 node/service/check；非法状态拒绝 */
    public void report(String node, String service, String checkId, Status status) {
        if (node == null || service == null || checkId == null || checkId.isBlank()) {
            throw new IllegalArgumentException("检查要素不能为空");
        }
        checks.put(node + "/" + service + "/" + checkId, status);
    }

    /** 节点下线：其全部检查转 critical */
    public int markNodeDown(String node) {
        int affected = 0;
        for (Map.Entry<String, Status> entry : checks.entrySet()) {
            if (entry.getKey().startsWith(node + "/")) {
                entry.setValue(Status.CRITICAL);
                affected++;
            }
        }
        return affected;
    }

    /** 服务聚合：各节点最差检查（无检查视为 passing）取最差 */
    public Status aggregate(List<String> nodes, String service) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("空节点列表拒绝聚合");
        }
        Status worst = Status.PASSING;
        for (String node : nodes) {
            worst = max(worst, instanceStatus(node, service));
        }
        return worst;
    }

    /** 单节点实例状态：其该服务全部检查最差（无检查 passing） */
    public Status instanceStatus(String node, String service) {
        String prefix = node + "/" + service + "/";
        Status worst = Status.PASSING;
        for (Map.Entry<String, Status> entry : checks.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                worst = max(worst, entry.getValue());
            }
        }
        return worst;
    }

    public List<String> failing() {
        List<String> critical = new ArrayList<>();
        for (Map.Entry<String, Status> entry : checks.entrySet()) {
            if (entry.getValue() == Status.CRITICAL) {
                critical.add(entry.getKey());
            }
        }
        return critical;
    }

    private static Status max(Status left, Status right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
