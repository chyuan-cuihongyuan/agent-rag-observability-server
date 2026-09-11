package cn.chyuan.ai.observability.domain.patrol.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一轮巡检汇总（工单 0137 S1）— /patrol/latest 端点与调度日志的输出形态。
 * avgScore 为本轮成功样本轻量分的均值（无成功样本时为 null，不造假默认值）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatrolRoundSummary {

    /** 轮次 ID（同轮所有记录共享） */
    private String roundId;
    /** 本轮拨测种子总数 */
    private int total;
    /** SUCCESS 计数 */
    private int success;
    /** FAIL 计数 */
    private int fail;
    /** TIMEOUT 计数 */
    private int timeout;
    /** 本轮平均轻量分（仅统计有分数的成功样本；无可统计样本时 null） */
    private Double avgScore;
    /** 轮次完成时间（yyyy-MM-dd HH:mm:ss） */
    private String finishedAt;
}
