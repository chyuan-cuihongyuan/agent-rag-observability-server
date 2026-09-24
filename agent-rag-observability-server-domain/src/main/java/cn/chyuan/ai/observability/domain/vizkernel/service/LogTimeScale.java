package cn.chyuan.ai.observability.domain.vizkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对数与时间比例尺（工单 0725 CH2，d3 思想）。
 * log 域正值约束/对数刻度按幂/时间刻度秒分时天月候选步长/计数逼近与取整。
 */
public final class LogTimeScale {

    private final double d0;
    private final double d1;
    private final double r0;
    private final double r1;

    public LogTimeScale(double d0, double d1, double r0, double r1) {
        if (d0 <= 0 || d1 <= 0) {
            throw new IllegalArgumentException("log 域须为正: " + d0 + "," + d1);
        }
        if (d0 == d1 || r0 == r1) {
            throw new IllegalArgumentException("域或值域退化");
        }
        this.d0 = Math.min(d0, d1);
        this.d1 = Math.max(d0, d1);
        this.r0 = r0;
        this.r1 = r1;
    }

    public double scale(double x) {
        if (x <= 0 || Double.isNaN(x)) {
            throw new IllegalArgumentException("log 输入须为正: " + x);
        }
        double t = (Math.log(x) - Math.log(d0)) / (Math.log(d1) - Math.log(d0));
        return r0 + t * (r1 - r0);
    }

    public double invert(double y) {
        double t = (y - r0) / (r1 - r0);
        return Math.exp(Math.log(d0) + t * (Math.log(d1) - Math.log(d0)));
    }

    /** 对数刻度：域内 10 的幂与其 2·5 倍（数量随跨度自适应） */
    public List<Double> ticks() {
        List<Double> ticks = new ArrayList<>();
        double span = Math.log10(d1 / d0);
        boolean fine = span < 2;
        for (int p = (int) Math.floor(Math.log10(d0)); p <= (int) Math.ceil(Math.log10(d1)); p++) {
            double base = Math.pow(10, p);
            long[] mults = fine ? new long[]{1, 2, 3, 5, 7, 10} : new long[]{1, 2, 5, 10};
            for (long m : mults) {
                double v = base * m;
                if (v >= d0 && v <= d1) {
                    ticks.add(v);
                }
            }
        }
        return ticks;
    }

    /** 时间刻度候选步长（毫秒）：秒分时天周月 */
    static final long[] TIME_STEPS = {
            1_000L, 5_000L, 15_000L, 30_000L,
            60_000L, 300_000L, 900_000L, 1_800_000L,
            3_600_000L, 7_200_000L, 21_600_000L, 43_200_000L,
            86_400_000L, 604_800_000L, 2_592_000_000L
    };

    /** 时间刻度：给近似目标数选步长并对齐取整 */
    public static List<Long> timeTicks(long fromMillis, long toMillis, int count) {
        if (toMillis <= fromMillis) {
            throw new IllegalArgumentException("时间区间倒置");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count 须 > 0");
        }
        long span = toMillis - fromMillis;
        long step = TIME_STEPS[TIME_STEPS.length - 1];
        for (long candidate : TIME_STEPS) {
            if (span / candidate <= count) {
                step = candidate;
                break;
            }
        }
        List<Long> ticks = new ArrayList<>();
        long first = (fromMillis + step - 1) / step * step;
        long guard = 0;
        for (long t = first; t <= toMillis && guard <= count + 2; t += step) {
            ticks.add(t);
            guard++;
        }
        return ticks;
    }

    /** 步长标签（轴格式化用） */
    public static String stepLabel(long step) {
        if (step % 2_592_000_000L == 0) {
            return step / 2_592_000_000L + "mo";
        }
        if (step % 86_400_000L == 0) {
            return step / 86_400_000L + "d";
        }
        if (step % 3_600_000L == 0) {
            return step / 3_600_000L + "h";
        }
        if (step % 60_000L == 0) {
            return step / 60_000L + "m";
        }
        return step / 1_000L + "s";
    }

    public double domainStart() {
        return d0;
    }

    public double domainEnd() {
        return d1;
    }
}
