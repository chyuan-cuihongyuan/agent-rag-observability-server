package cn.chyuan.ai.observability.domain.rulekernel.service;

/**
 * alert 状态机（工单 1039 EM3，prometheus alert 思想）。
 * inactive→pending→firing/条件翻转步进/非法跃迁拒绝。
 */
public final class AlertStates {

    public enum State {
        INACTIVE, PENDING, FIRING
    }

    private AlertStates() {
    }

    /** 合法迁移：INACTIVE→PENDING（条件触发）/PENDING→FIRING（for 满）/PENDING→INACTIVE 与 FIRING→INACTIVE（条件消失） */
    public static State transition(State current, State target) {
        if (current == target) {
            return current;
        }
        boolean legal = switch (current) {
            case INACTIVE -> target == State.PENDING;
            case PENDING -> target == State.FIRING || target == State.INACTIVE;
            case FIRING -> target == State.INACTIVE;
        };
        if (!legal) {
            throw new IllegalStateException("非法跃迁: " + current + " -> " + target);
        }
        return target;
    }

    /** 条件布尔到目标态：真 → PENDING（或经 for 满 FIRING），假 → INACTIVE */
    public static State targetFor(boolean condition) {
        return condition ? State.PENDING : State.INACTIVE;
    }
}
