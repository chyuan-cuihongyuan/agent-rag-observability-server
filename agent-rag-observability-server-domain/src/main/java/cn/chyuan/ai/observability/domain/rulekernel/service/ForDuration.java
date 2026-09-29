package cn.chyuan.ai.observability.domain.rulekernel.service;

/**
 * for 持续（工单 1040 EM4，prometheus for 思想）。
 * pending 满 for 才 firing/中途恢复回 inactive 重计时/未满不触发。
 */
public final class ForDuration {

    private ForDuration() {
    }

    /** pending 是否已满 for：now - activeAt >= forTicks 才触发；for=0 即到即触发 */
    public static boolean due(long activeAt, long now, long forTicks) {
        if (activeAt < 0 || forTicks < 0) {
            throw new IllegalArgumentException("activeAt/for 为负");
        }
        return now - activeAt >= forTicks;
    }

    /** 条件中断后重置：ActiveAt 清除（返回 -1 表示无挂起） */
    public static long reset() {
        return -1;
    }

    /** 重新计时：新 ActiveAt = 当前 tick */
    public static long restart(long now) {
        return now;
    }
}
