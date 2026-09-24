package cn.chyuan.ai.observability.domain.vizkernel.service;

import java.util.List;

/**
 * 形状生成器（工单 0728 CH5，d3 思想）。
 * line 折线 path/area 面积上下界/arc 环扇角度换算/空序列与单点处理。
 */
public final class Shapes {

    public record Point(double x, double y) {
    }

    private Shapes() {
    }

    /** 折线：M x0 y0 L x1 y1 ...；空序列空串；单点仅 M */
    public static String line(List<Point> points) {
        if (points == null || points.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            sb.append(i == 0 ? "M" : "L").append(fmt(p.x())).append(' ').append(fmt(p.y()));
            if (i < points.size() - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /** 面积：上界沿点序，下界沿基线返回闭合 Z */
    public static String area(List<Point> points, double baselineY) {
        if (points == null || points.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(line(points));
        sb.append(" L").append(fmt(points.get(points.size() - 1).x())).append(' ').append(fmt(baselineY));
        sb.append(" L").append(fmt(points.get(0).x())).append(' ').append(fmt(baselineY)).append(" Z");
        return sb.toString();
    }

    /** 环/扇：A 命令角度换算（弧度，0 指向 +x，顺时针为屏幕坐标正向） */
    public static String arc(double cx, double cy, double innerR, double outerR, double startAngle, double endAngle) {
        if (outerR < 0 || innerR < 0 || innerR > outerR) {
            throw new IllegalArgumentException("半径非法: inner=" + innerR + " outer=" + outerR);
        }
        double sweep = endAngle - startAngle;
        if (sweep < 0) {
            throw new IllegalArgumentException("角度倒置: " + startAngle + "->" + endAngle);
        }
        if (sweep == 0) {
            return "";
        }
        boolean fullCircle = Math.abs(sweep) >= 2 * Math.PI - 1e-9;
        double sx = cx + outerR * Math.cos(startAngle);
        double sy = cy + outerR * Math.sin(startAngle);
        int largeArc = sweep > Math.PI ? 1 : 0;
        StringBuilder sb = new StringBuilder();
        if (innerR > 0) {
            double ix = cx + innerR * Math.cos(endAngle);
            double iy = cy + innerR * Math.sin(endAngle);
            sb.append("M").append(fmt(sx)).append(' ').append(fmt(sy));
            sb.append(" A").append(fmt(outerR)).append(' ').append(fmt(outerR))
                    .append(" 0 ").append(fullCircle ? 1 : largeArc).append(" 1 ")
                    .append(fmt(ix)).append(' ').append(fmt(iy));
            sb.append(" L").append(fmt(cx + innerR * Math.cos(startAngle))).append(' ')
                    .append(fmt(cy + innerR * Math.sin(startAngle)));
            sb.append(" A").append(fmt(innerR)).append(' ').append(fmt(innerR))
                    .append(" 0 ").append(fullCircle ? 1 : largeArc).append(" 0 ")
                    .append(fmt(sx)).append(' ').append(fmt(sy));
            sb.append(" Z");
        } else {
            double ex = cx + outerR * Math.cos(endAngle);
            double ey = cy + outerR * Math.sin(endAngle);
            sb.append("M").append(fmt(cx)).append(' ').append(fmt(cy));
            sb.append(" L").append(fmt(sx)).append(' ').append(fmt(sy));
            sb.append(" A").append(fmt(outerR)).append(' ').append(fmt(outerR))
                    .append(" 0 ").append(fullCircle ? 1 : largeArc).append(" 1 ")
                    .append(fmt(ex)).append(' ').append(fmt(ey));
            sb.append(" Z");
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return String.valueOf(Math.round(v * 1e6) / 1e6);
    }
}
