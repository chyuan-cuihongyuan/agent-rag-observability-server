package cn.chyuan.ai.observability.domain.anomaly.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AX5-AX9 单测（工单 0409/0410/0411/0412/0413）：预测基线/LOF 离群/告警相关/恢复判定/异常记分。
 */
class AnomalyGovernanceTest {

    @Test
    void 预测基线趋势外推与水平退化() {
        ForecastBaseline baseline = new ForecastBaseline(5, 1.96, 0.05);
        // 线性上升序列（含微噪声）：y=2x，外推 h=3
        List<Double> series = List.of(0.0, 2.1, 3.9, 6.2, 7.8, 10.1, 11.9, 14.0);
        ForecastBaseline.Forecast forecast = baseline.forecast(series, 3);
        assertEquals(3, forecast.point().size());
        assertEquals(16.0, forecast.point().get(0), 1.5);
        assertTrue(forecast.upper().get(0) > forecast.point().get(0));
        assertTrue(forecast.lower().get(0) < forecast.point().get(0));
        assertFalse(forecast.flatDegenerate());
        // 水平序列：斜率不显著 → 退化水平基线
        List<Double> flat = List.of(5.0, 5.1, 4.9, 5.0, 5.05, 4.95, 5.0);
        ForecastBaseline.Forecast flatForecast = baseline.forecast(flat, 2);
        assertTrue(flatForecast.flatDegenerate());
        assertEquals(5.0, flatForecast.point().get(0), 0.5);
        // 预测区间对称且为正宽
        assertTrue(flatForecast.upper().get(0) > flatForecast.lower().get(0));
    }

    @Test
    void LOF离群因子手算对照与降级() {
        // 经典小样本：右上远点应为离群
        LofOutlierDetector detector = new LofOutlierDetector(2, 1.5, true);
        List<double[]> points = List.of(
                new double[]{0, 0}, new double[]{0, 1}, new double[]{1, 0},
                new double[]{1, 1}, new double[]{10, 10});
        List<LofOutlierDetector.ScoredPoint> scored = detector.detect(points);
        assertEquals(5, scored.size());
        assertTrue(scored.get(4).lof() > 1.0);
        assertTrue(scored.get(4).outlier());
        // k 越界降级：k=10 → k=n-1=4，不抛错
        LofOutlierDetector degrade = new LofOutlierDetector(10, 1.5, false);
        assertEquals(5, degrade.detect(points).size());
        // 维度不一致拒绝
        LofOutlierDetector strict = new LofOutlierDetector(2, 1.5, false);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> strict.detect(List.of(new double[]{0, 0}, new double[]{1})));
    }

    @Test
    void 告警同指纹聚类抑制与跨组关联() {
        AlertCorrelator correlator = new AlertCorrelator(60_000L);
        List<AlertCorrelator.AlertEvent> events = List.of(
                new AlertCorrelator.AlertEvent(1000L, "fp-cpu", "host-1", "cpu-high", "cpu 95%"),
                new AlertCorrelator.AlertEvent(5000L, "fp-cpu", "host-1", "cpu-high", "cpu 96%"),
                new AlertCorrelator.AlertEvent(10_000L, "fp-mem", "host-1", "mem-high", "mem 90%"),
                new AlertCorrelator.AlertEvent(200_000L, "fp-cpu", "host-1", "cpu-high", "cpu 97%"));
        AlertCorrelator.Correlation correlation = correlator.correlate(events);
        // 同窗同指纹 2 事件成组，第 2 条抑制；超窗另起组
        assertEquals(3, correlation.groups().size());
        assertEquals(1, correlation.suppressed().size());
        // 代表=组首
        assertEquals(1000L, correlation.groups().get(0).representative().timestampMs());
        // 跨组关联：host-1 上 fp-cpu 与 fp-mem
        assertEquals(1, correlation.crossLinks().size());
        assertTrue(correlation.crossLinks().get(0).contains("host-1"));
    }

    @Test
    void 恢复判定连续N点与中断重计() {
        RecoveryJudge judge = new RecoveryJudge(3);
        // 连续 3 点带内 → 恢复
        var ok = judge.judge(List.of(10.0, 10.5, 10.2), 9.0, 11.0);
        assertTrue(ok.resolved());
        assertTrue(ok.detail().contains("index=2"));
        // 带外中断重计（末端连续仅 2 点 < 3 → 不恢复）
        var reset = judge.judge(List.of(10.0, 20.0, 10.0, 10.5), 9.0, 11.0);
        assertFalse(reset.resolved());
        // 非法点数
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new RecoveryJudge(0));
    }

    @Test
    void 异常记分卡四指标评级与排序() {
        AnomalyScorecard scorecard = AnomalyScorecard.defaults();
        var good = scorecard.score(new AnomalyScorecard.Evaluation("ewma", 10, 9, 1, 30_000, 60_000));
        assertEquals("A", good.grade());
        assertTrue(good.weaknesses().isEmpty());
        var bad = scorecard.score(new AnomalyScorecard.Evaluation("cusum", 10, 5, 5, 400_000, 700_000));
        assertTrue(bad.score() < good.score());
        assertTrue(bad.weaknesses().size() >= 2);
        // 无真异常边界：检出率按 1.0
        assertEquals(1.0, scorecard.score(new AnomalyScorecard.Evaluation("x", 0, 0, 0, 0, 0)).detectionRate());
        // 检测器排序
        var ranking = scorecard.rank(List.of(
                new AnomalyScorecard.Evaluation("cusum", 10, 5, 5, 400_000, 700_000),
                new AnomalyScorecard.Evaluation("ewma", 10, 9, 1, 30_000, 60_000)));
        assertEquals("ewma", ranking.get(0).detector());
        // 零权重拒绝
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new AnomalyScorecard(0, 0, 0, 0));
    }
}
