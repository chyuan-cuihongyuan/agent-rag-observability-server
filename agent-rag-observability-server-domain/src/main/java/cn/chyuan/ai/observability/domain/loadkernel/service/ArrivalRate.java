package cn.chyuan.ai.observability.domain.loadkernel.service;

/**
 * 到达率执行器（工单 1146 EY4，k6 思想）。
 * constant-arrival-rate 按 timeUnit 均匀发射/preAllocatedVUs 不足拒绝/迭代耗时超间隔积压检出。
 */
public final class ArrivalRate {

    /** 发射间隔 ticks：timeUnit/rate，须整除且 ≥1 */
    public static long intervalTicks(int rate, int timeUnitTicks) {
        if (rate <= 0 || timeUnitTicks <= 0) {
            throw new IllegalArgumentException("rate 与 timeUnit 必须为正");
        }
        if (timeUnitTicks % rate != 0) {
            throw new IllegalArgumentException("timeUnit 须被 rate 整除以均匀发射: " + timeUnitTicks + "/" + rate);
        }
        long interval = timeUnitTicks / rate;
        return Math.max(1, interval);
    }

    /** 全程发射数 = rate × duration/timeUnit（整除口径） */
    public static long launches(int rate, int timeUnitTicks, int durationTicks) {
        return (long) rate * (durationTicks / timeUnitTicks);
    }

    /** 并发需求 = ⌈迭代耗时/发射间隔⌉；超 preAllocatedVUs 即积压拒绝 */
    public static void admitPreAllocated(int preAllocatedVus, long interval, int iterationTicks) {
        long needed = (iterationTicks + interval - 1) / interval;
        if (needed > preAllocatedVus) {
            throw new IllegalStateException("到达率积压超 preAllocatedVUs: 需 " + needed + " 预配 " + preAllocatedVus);
        }
    }

    /** 积压检出：迭代耗时超过发射间隔即积压 */
    public static boolean backlogged(long interval, int iterationTicks) {
        return iterationTicks > interval;
    }
}
