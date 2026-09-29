package cn.chyuan.ai.observability.domain.rulekernel.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import cn.chyuan.ai.observability.domain.rulekernel.service.RuleGroups.Group;
import cn.chyuan.ai.observability.domain.rulekernel.service.RuleGroups.Rule;

/**
 * 告警规则编排实现（工单 1044 EM8，prometheus 思想）。
 * 组合规则组/recording 写序列/alert 状态机/for 持续/模板/resolve/指纹去重；
 * tskernel 样本序列形态只读联动：样本形状串（形状数据不 import tskernel）。
 */
public final class RuleEngine implements RulePort {

    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Double> metrics = new HashMap<>();
    private final RecordingRules recordings = new RecordingRules();
    private final Resolves resolves = new Resolves();
    private long tick;

    @Override
    public synchronized void loadGroup(Group group) {
        if (groups.containsKey(group.name())) {
            throw new IllegalStateException("重复规则组: " + group.name());
        }
        groups.put(group.name(), group);
    }

    @Override
    public synchronized void setMetric(String expr, Double value) {
        if (value == null) {
            metrics.remove(expr);
        } else {
            metrics.put(expr, value);
        }
    }

    @Override
    public synchronized List<RecordingRules.Sample> eval() {
        tick++;
        List<RecordingRules.Sample> fired = new ArrayList<>();
        for (Group group : groups.values()) {
            if (tick % group.intervalTicks() != 0) {
                continue;
            }
            for (Rule rule : group.rules()) {
                if (rule.isAlerting()) {
                    evalAlert(rule, fired);
                } else {
                    evalRecording(rule);
                }
            }
        }
        return fired;
    }

    private void evalRecording(Rule rule) {
        Double value = metrics.get(rule.expr());
        if (value == null) {
            return;
        }
        recordings.write(rule.name(), rule.labels(), value, tick);
    }

    private void evalAlert(Rule rule, List<RecordingRules.Sample> fired) {
        Double value = metrics.get(rule.expr());
        boolean condition = value != null && value > 0;
        String fingerprint = Fingerprints.of(rule.name(), rule.labels());
        if (condition) {
            Resolves.Active active = resolves.trigger(fingerprint, rule.name(), rule.labels(), tick);
            if (active.state() == AlertStates.State.PENDING && ForDuration.due(active.activeAt(), tick, rule.forTicks())) {
                active.state = AlertStates.transition(active.state, AlertStates.State.FIRING);
                active.annotation = Templates.render(
                        rule.annotations().getOrDefault("summary", "{{$labels.alertname}} fired"),
                        withAlertName(rule, active.labels()), value);
                fired.add(new RecordingRules.Sample(rule.name(), active.labels(), value, tick));
            }
        } else {
            resolves.resolve(fingerprint);
        }
    }

    private static Map<String, String> withAlertName(Rule rule, Map<String, String> labels) {
        Map<String, String> merged = new TreeMap<>(labels);
        merged.putIfAbsent("alertname", rule.name());
        return merged;
    }

    @Override
    public synchronized List<RecordingRules.Sample> series() {
        return recordings.series();
    }

    @Override
    public synchronized List<RecordingRules.Sample> seriesOf(String record) {
        return recordings.seriesOf(record);
    }

    @Override
    public synchronized List<Resolves.Active> activeAlerts() {
        return resolves.all().stream()
                .filter(a -> a.state() != AlertStates.State.INACTIVE)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized long now() {
        return tick;
    }

    @Override
    public String sampleShape(RecordingRules.Sample sample) {
        String labelPart = new TreeMap<>(sample.labels()).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        return sample.metric() + labelPart + " " + Templates.format(sample.value()) + " @" + sample.tick();
    }
}
