package cn.chyuan.ai.observability.api.dto.patrol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 巡检轮次汇总 DTO（工单 0137 S1）— /patrol/latest 输出：汇总计数 + 本轮明细。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatrolRoundSummaryDTO {
    /** 轮次 ID（无任何记录时为 null） */
    private String roundId;
    private int total;
    private int success;
    private int fail;
    private int timeout;
    /** 本轮平均轻量分（无可统计样本时 null） */
    private Double avgScore;
    /** 轮次完成时间 */
    private String finishedAt;
    /** 本轮明细记录 */
    private List<PatrolRecordDTO> records;
}
