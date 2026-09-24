package cn.chyuan.ai.observability.domain.vizkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 线性比例尺（工单 0724 CH1，d3 思想）。
 * domain→range 仿射映射/invert/clamp 开关/nice 域扩展/NaN 拒绝。
 */
public final class LinearScale {

    private double d0;
    private double d1;
    private double r0;
    private double r1;
    private boolean clamp;

    public LinearScale(double d0, double d1, double r0, double r1) {
        setDomain(d0, d1);
        this.r0 = r0;
        this.r1 = r1;
    }

    public LinearScale setDomain(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            throw new IllegalArgumentException("domain 含 NaN");
        }
        if (a == b) {
            throw new IllegalArgumentException("domain 退化");
        }
        this.d0 = a;
        this.d1 = b;
        return this;
    }

    public LinearScale setRange(double a, double b) {
        this.r0 = a;
        this.r1 = b;
        return this;
    }

    public LinearScale clamp(boolean clamp) {
        this.clamp = clamp;
        return this;
    }

    public double scale(double x) {
        if (Double.isNaN(x)) {
            throw new IllegalArgumentException("输入 NaN");
        }
        double t = (x - d0) / (d1 - d0);
        double y = r0 + t * (r1 - r0);
        if (clamp) {
            double lo = Math.min(r0, r1);
            double hi = Math.max(r0, r1);
            y = Math.max(lo, Math.min(hi, y));
        }
        return y;
    }

    public double invert(double y) {
        if (Double.isNaN(y)) {
            throw new IllegalArgumentException("输入 NaN");
        }
        return d0 + (y - r0) / (r1 - r0) * (d1 - d0);
    }

    /** nice 扩域：按 1-2-5 幂步长向外取整（目标 count 个刻度） */
    public LinearScale nice(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count 须 > 0");
        }
        double step = niceStep((d1 - d0) / Math.max(1, count));
        double lo = Math.floor(d0 / step) * step;
        double hi = Math.ceil(d1 / step) * step;
        return setDomain(lo, hi);
    }

    /** 当前域按 1-2-5 步长生成刻度 */
    public List<Double> ticks(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count 须 > 0");
        }
        double step = niceStep((d1 - d0) / count);
        List<Double> ticks = new ArrayList<>();
        long guard = 0;
        for (double v = Math.ceil(d0 / step) * step; v <= d1 + step * 1e-6 && guard < 10000; v += step) {
            ticks.add(Math.round(v * 1e9) / 1e9);
            guard++;
        }
        return ticks;
    }

    static double niceStep(double raw) {
        if (raw <= 0 || Double.isNaN(raw)) {
            throw new IllegalArgumentException("步长非正");
        }
        double power = Math.pow(10, Math.floor(Math.log10(raw)));
        double norm = raw / power;
        double factor = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10;
        return factor * power;
    }

    public double domainStart() {
        return d0;
    }

    public double domainEnd() {
        return d1;
    }
}
