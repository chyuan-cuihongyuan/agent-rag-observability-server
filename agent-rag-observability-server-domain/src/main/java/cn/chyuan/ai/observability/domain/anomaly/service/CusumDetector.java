package cn.chyuan.ai.observability.domain.anomaly.service;

import java.util.ArrayList;
import java.util.List;

/**
 * CUSUM 变点检测（工单 0407 AX3）。
 * 序列+基线均值/允许偏移 k → 单侧或双侧累积和 → 超阈值 h 报 CHANGE_POINT（上/下漂移侧别）；
 * 报警后迟滞：连续 m 点回落带内才恢复。纯函数。
 */
public class CusumDetector {

    /** 状态 */
    public enum State {
        NORMAL, ALARM_UP, ALARM_DOWN, RECOVERED
    }

    /** 单点结果 */
    public record PointState(int index, double value, State state, double positiveSum, double negativeSum) {
    }

    private final double baselineMean;
    private final double slackK;
    private final double thresholdH;
    private final int recoverPoints;

    public CusumDetector(double baselineMean, double slackK, double thresholdH, int recoverPoints) {
        if (slackK < 0 || thresholdH <= 0 || recoverPoints < 1) {
            throw new IllegalArgumentException("k 不可为负、h 必须为正、恢复点数至少 1");
        }
        this.baselineMean = baselineMean;
        this.slackK = slackK;
        this.thresholdH = thresholdH;
        this.recoverPoints = recoverPoints;
    }

    /**
     * 双侧 CUSUM：S+ 累积上偏移、S- 累积下偏移；谁先超 h 报警；
     * 报警后连续 recoverPoints 点归零带内 → RECOVERED。
     */
    public List<PointState> judge(List<Double> series) {
        List<PointState> out = new ArrayList<>();
        double sPos = 0;
        double sNeg = 0;
        State state = State.NORMAL;
        int calmStreak = 0;
        for (int i = 0; i < (series == null ? 0 : series.size()); i++) {
            double deviation = series.get(i) - baselineMean;
            sPos = Math.max(0, sPos + deviation - slackK);
            sNeg = Math.max(0, sNeg - deviation - slackK);
            switch (state) {
                case NORMAL -> {
                    if (sPos > thresholdH) {
                        state = State.ALARM_UP;
                        calmStreak = 0;
                    } else if (sNeg > thresholdH) {
                        state = State.ALARM_DOWN;
                        calmStreak = 0;
                    }
                }
                case ALARM_UP, ALARM_DOWN -> {
                    if (Math.abs(deviation) <= slackK) {
                        // 观测已回到基线松弛带内：累积证据即刻作废（快速重启），迟滞只看连续带内点数
                        sPos = 0;
                        sNeg = 0;
                        calmStreak++;
                    } else {
                        calmStreak = 0;
                    }
                    if (calmStreak >= recoverPoints) {
                        state = State.RECOVERED;
                    }
                }
                default -> {
                    // RECOVERED：回到监视（视为 NORMAL 继续累积）
                    state = State.NORMAL;
                }
            }
            out.add(new PointState(i, series.get(i), state, sPos, sNeg));
        }
        return out;
    }
}
