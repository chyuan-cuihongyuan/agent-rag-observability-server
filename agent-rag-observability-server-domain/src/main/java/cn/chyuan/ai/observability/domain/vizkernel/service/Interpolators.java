package cn.chyuan.ai.observability.domain.vizkernel.service;

/**
 * 插值器（工单 0727 CH4，d3 思想）。
 * 数值 lerp/RGB 颜色插值/字符串数字内插/round 模式。
 */
public final class Interpolators {

    private final boolean round;

    public Interpolators(boolean round) {
        this.round = round;
    }

    /** 数值 lerp；round 模式输出取整 */
    public double number(double a, double b, double t) {
        if (t < 0 || t > 1 || Double.isNaN(t)) {
            throw new IllegalArgumentException("t 越界: " + t);
        }
        double v = a + (b - a) * t;
        return round ? Math.round(v) : v;
    }

    /** RGB 颜色插值（#rrggbb 形态，通道线性） */
    public String color(String from, String to, double t) {
        int[] a = parseColor(from);
        int[] b = parseColor(to);
        int r = (int) Math.round(number(a[0], b[0], t));
        int g = (int) Math.round(number(a[1], b[1], t));
        int bl = (int) Math.round(number(a[2], b[2], t));
        return String.format("#%02x%02x%02x", r, g, bl);
    }

    /** 字符串数字内插：同构字符串（数字位对应）按位 lerp，异构回退 t 阈值取端 */
    public String string(String a, String b, double t) {
        if (t < 0 || t > 1) {
            throw new IllegalArgumentException("t 越界: " + t);
        }
        java.util.regex.Matcher ma = NUMERIC_RUN.matcher(a);
        java.util.regex.Matcher mb = NUMERIC_RUN.matcher(b);
        StringBuilder out = new StringBuilder();
        int lastA = 0;
        boolean sameSkeleton = countRuns(a) == countRuns(b) && countRuns(a) > 0;
        if (!sameSkeleton) {
            return t < 0.5 ? a : b;
        }
        while (ma.find()) {
            if (!mb.find()) {
                return t < 0.5 ? a : b;
            }
            if (!a.substring(lastA, ma.start()).equals(b.substring(mb.start() - (ma.start() - lastA), mb.start()))) {
                return t < 0.5 ? a : b;
            }
            out.append(a, lastA, ma.start());
            double from = Double.parseDouble(ma.group());
            double to = Double.parseDouble(mb.group());
            double v = number(from, to, t);
            if (ma.group().contains(".")) {
                out.append(v);
            } else {
                out.append((long) Math.round(v));
            }
            lastA = ma.end();
        }
        out.append(a, lastA, a.length());
        return out.toString();
    }

    private static final java.util.regex.Pattern NUMERIC_RUN = java.util.regex.Pattern.compile("\\d+(\\.\\d+)?");

    private static int countRuns(String s) {
        java.util.regex.Matcher m = NUMERIC_RUN.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static int[] parseColor(String color) {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("颜色须为 #rrggbb: " + color);
        }
        return new int[]{
                Integer.parseInt(color.substring(1, 3), 16),
                Integer.parseInt(color.substring(3, 5), 16),
                Integer.parseInt(color.substring(5, 7), 16)
        };
    }
}
