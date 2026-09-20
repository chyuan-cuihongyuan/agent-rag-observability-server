package cn.chyuan.ai.observability.domain.tskernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警规则评估（工单 0485 BF6，prometheus alerting 思想）。
 * 阈值 + for 持续条件/状态机 inactive→pending→firing→resolved/恢复事件产出。
 * 时钟端口注入（纯函数无真实时钟）。
 */
public class AlertRuleEvaluator {

    /** 规则：指标 + 比较方向 + 阈值 + for 持续毫秒 */
    public record Rule(String ruleId, String metric, boolean aboveThreshold,
            double threshold, long forMillis) {
    }

    /** 状态机 */
    public enum State {
        INACTIVE, PENDING, FIRING, RESOLVED
    }

    /** 评估输入：某次 tick 的指标值（null 表示无数据） */
    public record Evaluation(String ruleId, State state, long stateSince, boolean transition, String event) {
    }

    private final Rule rule;
    private final Map<String, Long> breachSinceByInstance = new LinkedHashMap<>();
    private final Map<String, State> stateByInstance = new LinkedHashMap<>();

    public AlertRuleEvaluator(Rule rule) {
        if (rule.forMillis() < 0) {
            throw new IllegalArgumentException("for 持续须 ≥ 0");
        }
        this.rule = rule;
    }

    /** 阈值判定 */
    public boolean breaches(double value) {
        return rule.aboveThreshold() ? value > rule.threshold() : value < rule.threshold();
    }

    /** 逐 tick 评估（实例维度：同规则可多序列实例） */
    public synchronized Evaluation evaluate(String instance, Double value, long nowMillis) {
        State current = stateByInstance.getOrDefault(instance, State.INACTIVE);
        boolean breaching = value != null && breaches(value);
        Long breachSince = breachSinceByInstance.get(instance);
        if (breaching) {
            if (breachSince == null) {
                breachSinceByInstance.put(instance, nowMillis);
                breachSince = nowMillis;
            }
            boolean forSatisfied = nowMillis - breachSince >= rule.forMillis();
            State next = forSatisfied ? State.FIRING : State.PENDING;
            return transition(instance, current, next, nowMillis,
                    forSatisfied ? "firing" : "pending（持续条件未满）");
        }
        breachSinceByInstance.remove(instance);
        if (current == State.FIRING) {
            return transition(instance, current, State.RESOLVED, nowMillis, "resolved（恢复事件）");
        }
        if (current == State.PENDING) {
            return transition(instance, current, State.INACTIVE, nowMillis, "back to inactive（未触发即恢复）");
        }
        return new Evaluation(rule.ruleId(), State.INACTIVE, nowMillis, false, null);
    }

    private Evaluation transition(String instance, State current, State next, long nowMillis, String event) {
        stateByInstance.put(instance, next);
        boolean changed = current != next;
        return new Evaluation(rule.ruleId(), next, nowMillis, changed, changed ? event : null);
    }

    /** 实例状态快照 */
    public synchronized Map<String, State> snapshot() {
        return Map.copyOf(stateByInstance);
    }

    /** 全部状态历史（供恢复事件断言：RESOLVED 视为终态一次性事件） */
    public synchronized List<String> firingInstances() {
        List<String> instances = new ArrayList<>();
        stateByInstance.forEach((instance, state) -> {
            if (state == State.FIRING || state == State.RESOLVED) {
                instances.add(instance);
            }
        });
        return instances;
    }
}
