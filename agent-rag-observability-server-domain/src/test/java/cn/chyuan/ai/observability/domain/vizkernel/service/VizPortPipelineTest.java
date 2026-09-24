package cn.chyuan.ai.observability.domain.vizkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VizPort 组合管线测试（工单 0731 CH8，d3 思想）。
 * scale/tick/path/axis 统一编排/tskernel 序列点列只读联动形态（泛型入参不 import）/
 * 图表组合确定性/viz-kernel.enabled 默认关。
 */
class VizPortPipelineTest {

    @Test
    void portSequenceShapesFromTimeSeriesRows() {
        // tskernel 只读联动形态：序列查询结果点列（epochMillis, value）形状数据
        double[][] series = {
                {1_700_000_000_000L, 12}, {1_700_000_060_000L, 18}, {1_700_000_120_000L, 9}
        };
        VizPort port = VizPort.inMemory();
        String line = port.linePath(series);
        assertTrue(line.startsWith("M"), "折线以 M 起");
        assertEquals(3, line.split("L").length, "三点半段");

        String area = port.areaPath(series, 0);
        assertTrue(area.endsWith("Z"), "面积闭合");
        assertTrue(area.startsWith(line), "面积上界即折线");

        assertThrows(IllegalArgumentException.class, () -> port.linePath(new double[][]{{1, 2, 3}}));
        assertThrows(IllegalArgumentException.class, () -> port.linePath(null));
    }

    @Test
    void portNumericAxisNiceAndTimeAxis() {
        VizPort port = VizPort.inMemory();
        Axes.Axis axis = port.numericAxis(0, 97, 0, 400, 10, true);
        assertFalse(axis.ticks().isEmpty());
        assertTrue(axis.ticks().size() <= 12, "nice 刻度数量受控");
        for (int i = 1; i < axis.ticks().size(); i++) {
            assertTrue(axis.ticks().get(i).position() >= axis.ticks().get(i - 1).position(), "刻度单调");
        }
        assertTrue(axis.gridlines());

        List<Long> ticks = port.timeTicks(0, 86_400_000L, 8);
        assertEquals(5, ticks.size(), "六小时步长 epoch 对齐 5 刻度");
        assertEquals(0L, ticks.get(0));
    }

    @Test
    void portChartCompositionDeterministic() {
        VizPort port = VizPort.inMemory();
        Treemap.Node root = Treemap.Node.branch("root", List.of(
                Treemap.Node.leaf("get", 60), Treemap.Node.leaf("post", 30), Treemap.Node.leaf("other", 10)));
        List<Treemap.Rect> chart = port.treemap(root, 0, 0, 200, 100);
        assertEquals(3, chart.size());

        String rampMid = port.colorRamp("#1a2b3c", "#ffffff", 0.5);
        assertEquals(rampMid, port.colorRamp("#1a2b3c", "#ffffff", 0.5), "色带确定性");
        assertTrue(rampMid.matches("#[0-9a-f]{6}"));

        // 组合：比例尺+形状 确定性复现
        double[][] series = {{0, 0}, {1, 5}};
        assertEquals(port.linePath(series), port.linePath(series));
        assertEquals(port.linePath(series), port.linePath(new double[][]{{0, 0}, {1, 5}}), "形状数据等价");
    }
}
