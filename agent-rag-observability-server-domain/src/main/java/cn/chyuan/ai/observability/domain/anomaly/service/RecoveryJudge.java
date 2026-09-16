package cn.chyuan.ai.observability.domain.anomaly.service;

import java.util.List;

/**
 * 异常恢复判定（工单 0412 AX8）。
 * 控制带内连续 N 点 → RESOLVED；中断重计；事件全生命周期 OPEN→RESOLVED。纯函数。
 */
public class RecoveryJudge {

    /** 判定结果 */
    public record Judgment(boolean resolved, int inBandStreak, String detail) {
    }

    private final int requiredConsecutive;

    public RecoveryJudge(int requiredConsecutive) {
        if (requiredConsecutive < 1) {
            throw new IllegalArgumentException("连续点数至少 1");
        }
        this.requiredConsecutive = requiredConsecutive;
    }

    /**
     * 逐点判定：带内（lower<=value<=upper）连续累计，达阈值判定恢复；带外重计。
     *
     * @param postAlarmPoints 报警后的观测点序列
     * @param lower           控制带下限
     * @param upper           控制带上限
     */
    public Judgment judge(List<Double> postAlarmPoints, double lower, double upper) {
        int streak = 0;
        for (int i = 0; i < (postAlarmPoints == null ? 0 : postAlarmPoints.size()); i++) {
            double value = postAlarmPoints.get(i);
            if (value >= lower && value <= upper) {
                streak++;
                if (streak >= requiredConsecutive) {
                    return new Judgment(true, streak, "连续 " + streak + " 点回归带内于 index=" + i);
                }
            } else {
                streak = 0;
            }
        }
        return new Judgment(false, streak, "带内连续 " + streak + "/" + requiredConsecutive);
    }
}
