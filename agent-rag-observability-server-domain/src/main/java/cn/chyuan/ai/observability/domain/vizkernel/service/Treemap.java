package cn.chyuan.ai.observability.domain.vizkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * treemap 层次布局（工单 0729 CH6，d3 思想）。
 * 权重层次数据/squarify 行式最差纵横比切分/节点矩形输出/负权拒绝。
 */
public final class Treemap {

    /** 层次节点：叶带权重，枝聚合子节点 */
    public record Node(String name, double weight, List<Node> children) {
        public static Node leaf(String name, double weight) {
            return new Node(name, weight, List.of());
        }

        public static Node branch(String name, List<Node> children) {
            double sum = 0;
            for (Node child : children) {
                sum += child.weight();
            }
            return new Node(name, sum, children);
        }
    }

    /** 输出矩形：x y w h 与节点路径 */
    public record Rect(String path, double x, double y, double w, double h) {
    }

    private Treemap() {
    }

    /** 布局：根铺满矩形，叶子展开为矩形列表 */
    public static List<Rect> layout(Node root, double x, double y, double w, double h) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("矩形尺寸须为正");
        }
        if (root.weight() < 0) {
            throw new IllegalArgumentException("负权拒绝: " + root.name());
        }
        List<Rect> out = new ArrayList<>();
        place(root, x, y, w, h, root.name(), out);
        return out;
    }

    private static void place(Node node, double x, double y, double w, double h, String path, List<Rect> out) {
        if (node.children().isEmpty()) {
            out.add(new Rect(path, x, y, w, h));
            return;
        }
        double total = 0;
        for (Node child : node.children()) {
            if (child.weight() < 0) {
                throw new IllegalArgumentException("负权拒绝: " + child.name());
            }
            total += child.weight();
        }
        if (total <= 0) {
            throw new IllegalArgumentException("零总权: " + node.name());
        }
        double[] areas = new double[node.children().size()];
        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < areas.length; i++) {
            areas[i] = node.children().get(i).weight() / total * w * h;
            if (areas[i] > 0) {
                remaining.add(i);
            } else {
                out.add(new Rect(path + "/" + node.children().get(i).name(), x, y, 0, 0));
            }
        }
        double cx = x;
        double cy = y;
        double cw = w;
        double ch = h;
        while (!remaining.isEmpty()) {
            boolean vertical = cw >= ch;
            double side = vertical ? ch : cw;
            List<Integer> row = new ArrayList<>();
            List<Double> lengths = new ArrayList<>();
            double rowArea = 0;
            double best = Double.MAX_VALUE;
            while (!remaining.isEmpty()) {
                int next = remaining.get(0);
                double candArea = areas[next];
                double thickness = (rowArea + candArea) / side;
                List<Double> candLengths = new ArrayList<>(lengths);
                candLengths.add(candArea / thickness);
                double newWorst = worstRatio(candLengths, thickness);
                if (row.isEmpty() || newWorst <= best) {
                    best = newWorst;
                    rowArea += candArea;
                    row.add(remaining.remove(0));
                    lengths.add(candArea / thickness);
                } else {
                    break;
                }
            }
            double thickness = rowArea / side;
            double offset = 0;
            for (int idx : row) {
                double len = areas[idx] / thickness;
                double rx = vertical ? cx : cx + offset;
                double ry = vertical ? cy + offset : cy;
                place(node.children().get(idx), rx, ry,
                        vertical ? thickness : len, vertical ? len : thickness,
                        path + "/" + node.children().get(idx).name(), out);
                offset += len;
            }
            if (vertical) {
                cx += thickness;
                cw -= thickness;
            } else {
                cy += thickness;
                ch -= thickness;
            }
        }
    }

    /** 行内各项纵横比最差值 */
    private static double worstRatio(List<Double> lengths, double thickness) {
        double worst = 0;
        for (double len : lengths) {
            double r = ratio(len, thickness);
            if (r > worst) {
                worst = r;
            }
        }
        return worst;
    }

    private static double ratio(double a, double b) {
        if (a <= 0 || b <= 0) {
            return Double.MAX_VALUE / 2;
        }
        return Math.max(a / b, b / a);
    }
}
