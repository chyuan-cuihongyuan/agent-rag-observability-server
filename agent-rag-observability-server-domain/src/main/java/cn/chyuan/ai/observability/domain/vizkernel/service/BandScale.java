package cn.chyuan.ai.observability.domain.vizkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 序数比例尺（工单 0726 CH3，d3 思想）。
 * band（离散→区间均分 padding）/point（点对齐居中）/带宽取值/未知离散值追加或拒绝可配。
 */
public final class BandScale {

    private final List<String> domain = new ArrayList<>();
    private final double r0;
    private final double r1;
    private double paddingInner;
    private double paddingOuter;
    private double alignment = 0.5;
    private boolean appendUnknown = true;

    public BandScale(List<String> values, double r0, double r1) {
        if (r0 == r1) {
            throw new IllegalArgumentException("值域退化");
        }
        this.r0 = Math.min(r0, r1);
        this.r1 = Math.max(r0, r1);
        Set<String> seen = new LinkedHashSet<>(values);
        if (seen.size() != values.size()) {
            throw new IllegalArgumentException("离散域重复: " + values);
        }
        domain.addAll(seen);
    }

    public BandScale padding(double inner, double outer) {
        if (inner < 0 || outer < 0) {
            throw new IllegalArgumentException("padding 须 >= 0");
        }
        this.paddingInner = inner;
        this.paddingOuter = outer;
        return this;
    }

    public BandScale alignment(double alignment) {
        this.alignment = alignment;
        return this;
    }

    public BandScale appendUnknown(boolean appendUnknown) {
        this.appendUnknown = appendUnknown;
        return this;
    }

    private int indexOf(String value) {
        int at = domain.indexOf(value);
        if (at < 0) {
            if (!appendUnknown) {
                throw new IllegalArgumentException("未知离散值: " + value);
            }
            domain.add(value);
            at = domain.size() - 1;
        }
        return at;
    }

    /** 带宽几何：总步长 step 与内边距后的带宽 bandWidth */
    private double step() {
        int n = Math.max(1, domain.size());
        double innerSum = n - paddingInner;
        return n > 0 ? (r1 - r0) / (innerSum + 2 * paddingOuter) : 0;
    }

    /** band 模式：返回该值条带起点 */
    public double band(String value) {
        int at = indexOf(value);
        double step = step();
        double start = r0 + paddingOuter * step + at * step + paddingInner * step * 0.5;
        return start;
    }

    public double bandWidth() {
        double step = step();
        return step * (1 - paddingInner);
    }

    /** point 模式：返回该值对齐点（均分中点 + alignment∈[0,1] 平移） */
    public double point(String value) {
        int at = indexOf(value);
        int n = domain.size();
        double step = (r1 - r0) / Math.max(1, n);
        double center = r0 + at * step + step * 0.5;
        return center + (alignment - 0.5) * step;
    }

    public List<String> domain() {
        return List.copyOf(domain);
    }
}
