package cn.chyuan.ai.observability.domain.rulekernel.service;

import java.util.HashMap;
import java.util.Map;

/**
 * 恢复 resolve（工单 1042 EM6，prometheus resolve 思想）。
 * 条件消失 firing→inactive/ActiveAt 清除/再触发重新计时。
 */
public final class Resolves {

    /** 活跃告警条目：状态 + ActiveAt + 渲染注解 */
    public static final class Active {
        final String fingerprint;
        final String rule;
        final java.util.Map<String, String> labels;
        AlertStates.State state = AlertStates.State.PENDING;
        long activeAt = -1;
        String annotation = "";

        Active(String fingerprint, String rule, java.util.Map<String, String> labels) {
            this.fingerprint = fingerprint;
            this.rule = rule;
            this.labels = labels;
        }

        public String fingerprint() {
            return fingerprint;
        }

        public String rule() {
            return rule;
        }

        public java.util.Map<String, String> labels() {
            return labels;
        }

        public AlertStates.State state() {
            return state;
        }

        public long activeAt() {
            return activeAt;
        }

        public String annotation() {
            return annotation;
        }
    }

    private final Map<String, Active> actives = new HashMap<>();

    /** 条件触发：不存在则建目（重新计时 ActiveAt=now）；已存在保持原计时 */
    public synchronized Active trigger(String fingerprint, String rule, Map<String, String> labels, long now) {
        Active active = actives.get(fingerprint);
        if (active == null) {
            active = new Active(fingerprint, rule, labels);
            active.activeAt = now;
            actives.put(fingerprint, active);
        }
        return active;
    }

    /** 条件消失：FIRING→INACTIVE / PENDING→INACTIVE，ActiveAt 清除并移除（指纹可复用） */
    public synchronized boolean resolve(String fingerprint) {
        Active active = actives.remove(fingerprint);
        if (active == null) {
            return false;
        }
        active.state = AlertStates.transition(active.state, AlertStates.State.INACTIVE);
        active.activeAt = ForDuration.reset();
        return true;
    }

    public synchronized Active get(String fingerprint) {
        return actives.get(fingerprint);
    }

    public synchronized java.util.List<Active> all() {
        return java.util.List.copyOf(actives.values());
    }

    public synchronized boolean isActive(String fingerprint) {
        return actives.containsKey(fingerprint);
    }
}
