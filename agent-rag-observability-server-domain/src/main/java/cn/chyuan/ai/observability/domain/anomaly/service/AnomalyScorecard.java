package cn.chyuan.ai.observability.domain.anomaly.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 异常记分卡（工单 0413 AX9，沿 AF09 先例）。
 * 检测器评估集（标注真值×检测输出）→ 检出率/误报率/平均检测延迟/平均恢复时长四指标加权记分
 * + 评级与检测器间比较排序。权重可配。纯函数。
 */
public class AnomalyScorecard {

    /** 单检测器评估输入 */
    public record Evaluation(String detector, int trueAnomalies, int detected, int falseAlarms,
                             double avgDetectionDelayMs, double avgRecoveryMs) {
    }

    /** 记分结果 */
    public record Score(double detectionRate, double falseAlarmRate, double avgDelayMs, double avgRecoveryMs,
                        double score, String grade, List<String> weaknesses) {
    }

    private final double wDetect;
    private final double wFalse;
    private final double wDelay;
    private final double wRecovery;

    public AnomalyScorecard(double wDetect, double wFalse, double wDelay, double wRecovery) {
        double total = wDetect + wFalse + wDelay + wRecovery;
        if (total <= 0) {
            throw new IllegalArgumentException("权重之和必须为正");
        }
        this.wDetect = wDetect / total;
        this.wFalse = wFalse / total;
        this.wDelay = wDelay / total;
        this.wRecovery = wRecovery / total;
    }

    public static AnomalyScorecard defaults() {
        return new AnomalyScorecard(0.35, 0.3, 0.2, 0.15);
    }

    /** 单检测器记分 */
    public Score score(Evaluation evaluation) {
        double detectionRate = evaluation.trueAnomalies() == 0
                ? 1.0
                : Math.min(1.0, (double) evaluation.detected() / evaluation.trueAnomalies());
        int alarms = evaluation.detected() + evaluation.falseAlarms();
        double falseAlarmRate = alarms == 0 ? 0.0 : (double) evaluation.falseAlarms() / alarms;
        // 延迟/恢复归一（1 小时封顶：越短越好）
        double delayScore = 1.0 - Math.min(1.0, evaluation.avgDetectionDelayMs() / 3_600_000.0);
        double recoveryScore = 1.0 - Math.min(1.0, evaluation.avgRecoveryMs() / 3_600_000.0);
        double total = round(wDetect * detectionRate + wFalse * (1 - falseAlarmRate)
                + wDelay * delayScore + wRecovery * recoveryScore);
        String grade = total >= 0.85 ? "A" : total >= 0.7 ? "B" : "C";
        List<String> weaknesses = new ArrayList<>();
        if (detectionRate < 0.8) {
            weaknesses.add("检出率低（" + round(detectionRate) + "）");
        }
        if (falseAlarmRate > 0.2) {
            weaknesses.add("误报率高（" + round(falseAlarmRate) + "）");
        }
        if (evaluation.avgDetectionDelayMs() > 300_000) {
            weaknesses.add("检测延迟偏大（" + Math.round(evaluation.avgDetectionDelayMs() / 1000.0) + "s）");
        }
        if (evaluation.avgRecoveryMs() > 600_000) {
            weaknesses.add("恢复时长偏长（" + Math.round(evaluation.avgRecoveryMs() / 1000.0) + "s）");
        }
        return new Score(round(detectionRate), round(falseAlarmRate),
                evaluation.avgDetectionDelayMs(), evaluation.avgRecoveryMs(), total, grade, weaknesses);
    }

    /** 检测器比较排序（得分降序，同级按误报率升序） */
    public List<ScoredDetector> rank(List<Evaluation> evaluations) {
        List<ScoredDetector> out = new ArrayList<>();
        for (Evaluation evaluation : evaluations) {
            Score score = score(evaluation);
            out.add(new ScoredDetector(evaluation.detector(), score));
        }
        out.sort(Comparator.comparingDouble((ScoredDetector d) -> d.score().score()).reversed()
                .thenComparingDouble(d -> d.score().falseAlarmRate()));
        return out;
    }

    public record ScoredDetector(String detector, Score score) {
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
