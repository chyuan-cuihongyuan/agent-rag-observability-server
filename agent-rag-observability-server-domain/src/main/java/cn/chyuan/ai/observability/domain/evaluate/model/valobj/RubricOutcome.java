package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rubric 执行整体结果（工单 0133 R1）：逐维度 verdict + 加权综合分 + unknown 占比。
 * <p>
 * 综合分 = Σ(可判定维度得分 × 权重) / Σ(可判定维度权重)——unknown 维度剔除后归一化，
 * 避免「标准不清晰」直接拉低分数；unknown 占比单独作为 Rubric 质量的反查指标。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RubricOutcome {
    /** 逐维度判定结果 */
    private List<DimensionVerdict> dimensionVerdicts;
    /** 加权综合分（0-1）；全部 unknown 时为 0 */
    private double weightedScore;
    /** unknown 维度占比（0-1），= unknownCount / dimensionCount */
    private double unknownRatio;
    /** unknown 维度数 */
    private int unknownCount;
    /** 维度总数 */
    private int dimensionCount;
}
