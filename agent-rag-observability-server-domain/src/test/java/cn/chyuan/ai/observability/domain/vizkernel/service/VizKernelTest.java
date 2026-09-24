package cn.chyuan.ai.observability.domain.vizkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 可视化内核测试（工单 0724-0730 CH1-CH7，d3 思想）。
 * 线性/对数/时间/序数比例尺与插值器/形状生成/treemap/坐标轴。
 */
class VizKernelTest {

    @Test
    void linearScaleMappingInvertClampNice() {
        LinearScale scale = new LinearScale(0, 100, 0, 500);
        assertEquals(250, scale.scale(50));
        assertEquals(50, scale.invert(250));
        assertTrue(scale.scale(150) > 500, "无 clamp 越界");
        assertTrue(scale.scale(-10) < 0);
        scale.clamp(true);
        assertEquals(500, scale.scale(150));
        assertEquals(0, scale.scale(-10));

        LinearScale nice = new LinearScale(3, 97, 0, 1).clamp(false);
        nice.nice(10);
        assertTrue(nice.domainStart() <= 3, "nice 向外扩域");
        assertTrue(nice.domainEnd() >= 97);
        List<Double> ticks = nice.ticks(10);
        assertFalse(ticks.isEmpty());
        assertEquals(0.0, ticks.get(0));
        assertEquals(100.0, ticks.get(ticks.size() - 1));
        assertEquals(11, ticks.size(), "0..100 步 10 含两端");

        assertThrows(IllegalArgumentException.class, () -> new LinearScale(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LinearScale(0, 100, 0, 1).scale(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> nice.nice(0));
    }

    @Test
    void logTimeScaleTicksAndConstraints() {
        LogTimeScale log = new LogTimeScale(1, 100, 0, 1);
        assertEquals(0.5, log.scale(10), 1e-9);
        assertEquals(10, log.invert(0.5), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new LogTimeScale(0, 100, 0, 1), "log 域须正");
        assertThrows(IllegalArgumentException.class, () -> log.scale(0));
        List<Double> ticks = new LogTimeScale(1, 1000, 0, 1).ticks();
        assertTrue(ticks.contains(1.0) && ticks.contains(10.0) && ticks.contains(100.0) && ticks.contains(1000.0));
        assertTrue(ticks.contains(2.0) && ticks.contains(5.0), "粗刻度含 1-2-5");

        List<Long> timeTicks = LogTimeScale.timeTicks(0, 3_600_000L, 6);
        assertEquals(5, timeTicks.size(), "epoch 对齐 0 起 15 分钟步长 5 刻度");
        assertEquals(0L, timeTicks.get(0), "epoch 对齐取整");
        assertEquals("15m", LogTimeScale.stepLabel(900_000L));
        assertThrows(IllegalArgumentException.class, () -> LogTimeScale.timeTicks(10, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> LogTimeScale.timeTicks(0, 100, 0));
    }

    @Test
    void bandPointScalePaddingAndUnknown() {
        BandScale band = new BandScale(List.of("a", "b", "c"), 0, 300).padding(0.2, 0.1);
        assertEquals(20.0, band.band("a"), "外边距 10 + 内边距半 10");
        assertEquals(80.0, band.bandWidth());
        assertEquals(220.0, band.band("c"));
        assertEquals(300.0, band.band("c") + band.bandWidth(), "恰好铺满值域");

        BandScale point = new BandScale(List.of("a", "b", "c"), 0, 300);
        assertEquals(50.0, point.point("a"), "点对齐居中");
        assertEquals(250.0, point.point("c"));

        BandScale append = new BandScale(List.of("a"), 0, 100);
        append.band("new");
        assertEquals(2, append.domain().size(), "未知值追加");
        BandScale strict = new BandScale(List.of("a"), 0, 100).appendUnknown(false);
        assertThrows(IllegalArgumentException.class, () -> strict.band("new"));
        assertThrows(IllegalArgumentException.class, () -> new BandScale(List.of("x", "x"), 0, 100), "重复域拒绝");
    }

    @Test
    void interpolatorsNumberColorString() {
        Interpolators plain = new Interpolators(false);
        assertEquals(2.5, plain.number(0, 10, 0.25));
        Interpolators rounding = new Interpolators(true);
        assertEquals(3, rounding.number(0, 10, 0.25));
        assertThrows(IllegalArgumentException.class, () -> plain.number(0, 1, 1.5));

        assertEquals("#808080", plain.color("#000000", "#ffffff", 0.5));
        assertEquals("#1a2b3c", plain.color("#1a2b3c", "#000000", 0), "t=0 取起点色");
        assertEquals("#000000", plain.color("#1a2b3c", "#000000", 1));
        assertThrows(IllegalArgumentException.class, () -> plain.color("red", "#ffffff", 0.5));

        assertEquals("v1.5", plain.string("v1.2", "v1.8", 0.5));
        assertEquals("12 items", plain.string("4 items", "20 items", 0.5));
        assertEquals("a", plain.string("a", "b", 0.2), "异构回退取端");
        assertEquals("b", plain.string("a", "b", 0.8));
    }

    @Test
    void shapesLineAreaArc() {
        assertEquals("", Shapes.line(List.of()));
        assertEquals("M1 2", Shapes.line(List.of(new Shapes.Point(1, 2))));
        assertEquals("M0 0 L10 20", Shapes.line(List.of(new Shapes.Point(0, 0), new Shapes.Point(10, 20))));

        String area = Shapes.area(List.of(new Shapes.Point(0, 10), new Shapes.Point(10, 5)), 0);
        assertTrue(area.startsWith("M0 10 L10 5"));
        assertTrue(area.endsWith("Z"), "面积闭合");
        assertTrue(area.contains("L10 0 L0 0"), "下界沿基线");

        String quarter = Shapes.arc(0, 0, 0, 10, 0, Math.PI / 2);
        assertTrue(quarter.startsWith("M0 0"), "扇形圆心起");
        assertTrue(quarter.contains("A10 10"), "外弧半径");
        String ring = Shapes.arc(0, 0, 5, 10, 0, Math.PI / 2);
        assertTrue(ring.contains("A5 5"), "内弧");
        assertTrue(ring.contains("Z"));
        String full = Shapes.arc(0, 0, 0, 10, 0, 2 * Math.PI);
        assertTrue(full.contains(" 1 "), "整圆 large-arc");
        assertThrows(IllegalArgumentException.class, () -> Shapes.arc(0, 0, 12, 10, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Shapes.arc(0, 0, 0, 10, 1, 0));
        assertEquals("", Shapes.arc(0, 0, 0, 10, 1, 1), "零角空");
    }

    @Test
    void treemapSquarifyLayout() {
        Treemap.Node root = Treemap.Node.branch("root", List.of(
                Treemap.Node.leaf("a", 50), Treemap.Node.leaf("b", 50)));
        List<Treemap.Rect> rects = Treemap.layout(root, 0, 0, 100, 100);
        assertEquals(2, rects.size());
        Treemap.Rect a = rects.get(0);
        Treemap.Rect b = rects.get(1);
        assertEquals(5000.0, a.w() * a.h());
        assertEquals(5000.0, b.w() * b.h());
        assertEquals("root/a", a.path());

        Treemap.Node quarters = Treemap.Node.branch("q", List.of(
                Treemap.Node.leaf("w", 25), Treemap.Node.leaf("x", 25),
                Treemap.Node.leaf("y", 25), Treemap.Node.leaf("z", 25)));
        List<Treemap.Rect> four = Treemap.layout(quarters, 0, 0, 100, 100);
        assertEquals(4, four.size());
        double totalArea = 0;
        for (Treemap.Rect rect : four) {
            totalArea += rect.w() * rect.h();
            assertTrue(rect.w() <= 100.0001 && rect.h() <= 100.0001);
        }
        assertEquals(10_000.0, totalArea, 0.001, "面积守恒");

        Treemap.Node nested = Treemap.Node.branch("n", List.of(
                Treemap.Node.branch("p", List.of(Treemap.Node.leaf("p1", 3), Treemap.Node.leaf("p2", 1))),
                Treemap.Node.leaf("s", 4)));
        List<Treemap.Rect> deep = Treemap.layout(nested, 0, 0, 100, 50);
        assertEquals(3, deep.size(), "层次展开到叶");
        assertTrue(deep.stream().anyMatch(r -> r.path().equals("n/p/p1")));

        assertThrows(IllegalArgumentException.class, () -> Treemap.layout(
                Treemap.Node.branch("neg", List.of(Treemap.Node.leaf("a", -1))), 0, 0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> Treemap.layout(
                Treemap.Node.branch("zero", List.of(Treemap.Node.leaf("a", 0))), 0, 0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> Treemap.layout(root, 0, 0, 0, 10));
    }

    @Test
    void axesBuildThinAndFormat() {
        LinearScale scale = new LinearScale(0, 100, 0, 200);
        List<Double> values = new java.util.ArrayList<>();
        for (int v = 0; v <= 100; v += 5) {
            values.add((double) v);
        }
        Axes.Axis axis = Axes.build(scale, values, 25, true);
        assertTrue(axis.gridlines());
        assertEquals(0, axis.extentStart());
        assertEquals(200, axis.extentEnd());
        List<Axes.Tick> ticks = axis.ticks();
        assertFalse(ticks.isEmpty());
        for (int i = 1; i < ticks.size(); i++) {
            assertTrue(ticks.get(i).position() - ticks.get(i - 1).position() >= 25, "重叠抽稀保间隔");
        }
        assertEquals("0", ticks.get(0).label());

        List<Integer> thinned = Axes.thinIndices(10, 3);
        assertTrue(thinned.size() <= 3);
        assertEquals(0, thinned.get(0));

        assertEquals("42", Axes.format(42));
        assertEquals("1.5k", Axes.format(1500));
        assertEquals("2.5M", Axes.format(2_500_000));
        assertThrows(IllegalArgumentException.class, () -> Axes.format(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Axes.build(scale, values, 0, true));
    }
}
