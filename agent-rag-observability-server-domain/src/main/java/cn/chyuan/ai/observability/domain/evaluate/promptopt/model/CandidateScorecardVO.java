package cn.chyuan.ai.observability.domain.evaluate.promptopt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 候选提示记分卡值对象（AO3：候选×评测集聚合得分）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CandidateScorecardVO {

    /** 候选提示 */
    private String prompt;

    /** 评测例数 */
    private int caseCount;

    /** 平均得分 */
    private double meanScore;

    /** 通过例数（得分≥通过线） */
    private int passCount;

    /** 通过率 */
    private double passRate;

    /** 失败例清单（输入摘要+得分） */
    private List<String> failures;
}
