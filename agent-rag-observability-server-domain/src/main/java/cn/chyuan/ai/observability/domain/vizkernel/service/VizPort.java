package cn.chyuan.ai.observability.domain.vizkernel.service;

import java.util.List;

/**
 * 可视化端口（工单 0731 CH8，d3 思想）。
 * scale/tick/path/axis 入口统一编排/tskernel 序列点列只读联动形态（泛型入参不 import）/
 * viz-kernel.enabled 默认关（开启才改变行为）。
 */
public interface VizPort {

    /** 折线 path（点列为时序序列形状数据：x 值/y 值） */
    String linePath(double[][] points);

    /** 面积 path */
    String areaPath(double[][] points, double baselineY);

    /** 数值轴：nice 域 + 刻度生成 + 重叠抽稀 */
    Axes.Axis numericAxis(double from, double to, double rangeFrom, double rangeTo, int approxTicks, boolean gridlines);

    /** 时间轴：候选步长对齐刻度 */
    List<Long> timeTicks(long fromMillis, long toMillis, int count);

    /** treemap 布局 */
    List<Treemap.Rect> treemap(Treemap.Node root, double x, double y, double w, double h);

    /** 颜色梯度（插值器） */
    String colorRamp(String from, String to, double t);

    static VizPort inMemory() {
        return new InMemoryViz();
    }
}

final class InMemoryViz implements VizPort {

    @Override
    public String linePath(double[][] points) {
        return Shapes.line(toPoints(points));
    }

    @Override
    public String areaPath(double[][] points, double baselineY) {
        return Shapes.area(toPoints(points), baselineY);
    }

    @Override
    public Axes.Axis numericAxis(double from, double to, double rangeFrom, double rangeTo, int approxTicks, boolean gridlines) {
        LinearScale scale = new LinearScale(from, to, rangeFrom, rangeTo).clamp(true);
        scale.nice(approxTicks);
        return Axes.build(scale, scale.ticks(approxTicks), 24, gridlines);
    }

    @Override
    public List<Long> timeTicks(long fromMillis, long toMillis, int count) {
        return LogTimeScale.timeTicks(fromMillis, toMillis, count);
    }

    @Override
    public List<Treemap.Rect> treemap(Treemap.Node root, double x, double y, double w, double h) {
        return Treemap.layout(root, x, y, w, h);
    }

    @Override
    public String colorRamp(String from, String to, double t) {
        return new Interpolators(false).color(from, to, t);
    }

    private static List<Shapes.Point> toPoints(double[][] raw) {
        if (raw == null) {
            throw new IllegalArgumentException("点列空");
        }
        List<Shapes.Point> points = new java.util.ArrayList<>();
        for (double[] p : raw) {
            if (p.length != 2) {
                throw new IllegalArgumentException("点须为二维");
            }
            points.add(new Shapes.Point(p[0], p[1]));
        }
        return points;
    }
}
