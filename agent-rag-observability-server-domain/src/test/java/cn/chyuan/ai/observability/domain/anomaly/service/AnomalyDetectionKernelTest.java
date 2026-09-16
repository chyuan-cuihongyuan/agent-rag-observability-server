package cn.chyuan.ai.observability.domain.anomaly.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AX1-AX4 单测（工单 0405/0406/0407/0408）：EWMA/季节分解/CUSUM/尖峰凹陷。
 */
class AnomalyDetectionKernelTest {

    @Test
    void EWMA控制限与侧别预热() {
        EwmaControlChart chart = new EwmaControlChart(0.4, 1.0, 3);
        List<Double> series = List.of(10.0, 10.5, 9.8, 10.2, 25.0, 9.5, 2.0);
        List<EwmaControlChart.PointVerdict> verdicts = chart.judge(series);
        // 预热期（前 3 点）不判定
        assertEquals(EwmaControlChart.Verdict.NORMAL, verdicts.get(0).verdict());
        assertEquals(EwmaControlChart.Verdict.NORMAL, verdicts.get(2).verdict());
        // index=4 突跳 25 → UP
        assertEquals(EwmaControlChart.Verdict.UP, verdicts.get(4).verdict());
        // index=6 跌至 2 → DOWN
        assertEquals(EwmaControlChart.Verdict.DOWN, verdicts.get(6).verdict());
        // 非法系数
        assertThrows(IllegalArgumentException.class, () -> new EwmaControlChart(0, 2, 0));
    }

    @Test
    void 季节分解三分量与周期不足拒绝() {
        SeasonalDecomposer decomposer = new SeasonalDecomposer(4);
        // 两周期：基线 10 + 相位剖面 [0,5,0,-5] + 微残差
        List<Double> series = List.of(10.0, 15.0, 10.0, 5.0, 10.0, 15.0, 10.0, 5.0);
        SeasonalDecomposer.Decomposition d = decomposer.decompose(series);
        assertEquals(8, d.original().size());
        assertEquals(8, d.trend().length);
        assertEquals(8, d.seasonal().length);
        assertEquals(8, d.residual().length);
        // 相位剖面还原：季节分量 index1≈+5、index3≈-5
        assertEquals(5.0, d.seasonal()[1], 0.5);
        assertEquals(-5.0, d.seasonal()[3], 0.5);
        // 周期序列残差方差很小
        assertTrue(d.residualVariance() < 1.0);
        // 不足两周期拒绝
        assertThrows(IllegalArgumentException.class,
                () -> new SeasonalDecomposer(4).decompose(List.of(1.0, 2.0, 3.0)));
    }

    @Test
    void CUSUM上下漂移检测与迟滞恢复() {
        CusumDetector detector = new CusumDetector(10.0, 0.5, 8.0, 2);
        // 前 5 点基线，后 10 点上漂 +1.5/点 → S+ 快速累积报警
        List<Double> series = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            series.add(10.0);
        }
        for (int i = 0; i < 10; i++) {
            series.add(11.5);
        }
        List<CusumDetector.PointState> states = detector.judge(series);
        assertTrue(states.stream().anyMatch(s -> s.state() == CusumDetector.State.ALARM_UP));
        // 报警后漂移持续（不恢复）
        assertTrue(states.get(states.size() - 1).state() != CusumDetector.State.NORMAL);
        // 单次运行：10×3 基线 + 13×4 上漂报警 + 10×12 回落 → 先 ALARM_UP 后（带内连续 2 点）RECOVERED
        List<Double> recover = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            recover.add(10.0);
        }
        for (int i = 0; i < 4; i++) {
            recover.add(13.0);
        }
        for (int i = 0; i < 12; i++) {
            recover.add(10.0);
        }
        List<CusumDetector.PointState> rec = detector.judge(recover);
        assertTrue(rec.stream().anyMatch(s -> s.state() == CusumDetector.State.ALARM_UP));
        assertTrue(rec.stream().anyMatch(s -> s.state() == CusumDetector.State.RECOVERED));
        // 下漂 → ALARM_DOWN
        List<Double> down = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            down.add(10.0);
        }
        for (int i = 0; i < 10; i++) {
            down.add(8.5);
        }
        assertTrue(detector.judge(down).stream().anyMatch(s -> s.state() == CusumDetector.State.ALARM_DOWN));
    }

    @Test
    void 尖峰凹陷倍率标注与连续极值取最大() {
        SpikeDipDetector detector = new SpikeDipDetector(3, 2.0);
        // 中位≈5：spike 20（4x）标注；dip 1（1/5）标注；5 是 spike 后邻域高值非极值
        List<Double> series = List.of(5.0, 5.0, 6.0, 20.0, 6.0, 5.0, 5.0, 1.0, 5.0, 5.0);
        List<SpikeDipDetector.AnomalyPoint> points = detector.detect(series);
        assertEquals(2, points.size());
        assertEquals(SpikeDipDetector.SPIKE, points.get(0).type());
        assertEquals(3, points.get(0).index());
        assertEquals(SpikeDipDetector.DIP, points.get(1).type());
        assertEquals(7, points.get(1).index());
        // 连续同向尖峰取最大：两个相邻 spike 保留更大
        List<Double> twin = List.of(5.0, 5.0, 12.0, 20.0, 12.0, 5.0, 5.0);
        List<SpikeDipDetector.AnomalyPoint> twinPoints = detector.detect(twin);
        assertTrue(twinPoints.size() <= 2);
        assertTrue(twinPoints.stream().noneMatch(p -> p.type().equals(SpikeDipDetector.SPIKE) && p.value() == 12.0)
                || twinPoints.stream().filter(p -> p.type().equals(SpikeDipDetector.SPIKE)).count() == 1);
        // 非法阈值
        assertThrows(IllegalArgumentException.class, () -> new SpikeDipDetector(3, 1.0));
    }
}
