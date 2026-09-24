package cn.chyuan.ai.observability.domain.vizkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 坐标轴（工单 0730 CH7，d3 思想）。
 * 刻度线标签网格线生成/标签重叠抽稀步长选择/轴端延伸/数值格式化。
 */
public final class Axes {

    /** 刻度：位置（已映射值域）与标签 */
    public record Tick(double position, String label) {
    }

    /** 轴描述：刻度与网格线开关 */
    public record Axis(List<Tick> ticks, boolean gridlines, double extentStart, double extentEnd) {
    }

    private Axes() {
    }

    /** 生成轴：刻度值经 scale 映射，标签按重叠抽稀（最小间隔 minGapPx） */
    public static Axis build(LinearScale scale, List<Double> values, double minGapPx, boolean gridlines) {
        if (minGapPx <= 0) {
            throw new IllegalArgumentException("最小间隔须为正");
        }
        List<Tick> ticks = new ArrayList<>();
        double lastPosition = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            double position = scale.scale(value);
            if (position - lastPosition < minGapPx) {
                continue;
            }
            ticks.add(new Tick(position, format(value)));
            lastPosition = position;
        }
        return new Axis(ticks, gridlines, scale.scale(scale.domainStart()), scale.scale(scale.domainEnd()));
    }

    /** 标签重叠抽稀：至多保留 maxKeep 个，步长=ceil(count/maxKeep) */
    public static List<Integer> thinIndices(int count, int maxKeep) {
        if (count < 0 || maxKeep <= 0) {
            throw new IllegalArgumentException("参数非法");
        }
        int step = Math.max(1, (int) Math.ceil((double) count / maxKeep));
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < count; i += step) {
            out.add(i);
        }
        return out;
    }

    /** 数值格式化：去尾零、千位以下原样、大数 k/M 缩写 */
    public static String format(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("NaN 不可格式化");
        }
        if (Math.abs(value) >= 1_000_000) {
            return trim(value / 1_000_000) + "M";
        }
        if (Math.abs(value) >= 1_000) {
            return trim(value / 1_000) + "k";
        }
        return trim(value);
    }

    private static String trim(double value) {
        double rounded = Math.round(value * 100) / 100.0;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        String s = String.valueOf(rounded);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
