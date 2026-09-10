package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 任务级评测汇总值对象（工单 0135 R3 / 0136 R4）— Pass@k 统计结果 + 门禁判定输入：
 * <ul>
 *   <li>trials：试验次数 k（k=1 为旧行为）</li>
 *   <li>sampleCount：样本数（数据集条目数；结果行数 = sampleCount × trials）</li>
 *   <li>avgOverall：综合分均值（全部 trial 全部样本的 overall 均值，与 avg_overall_score 同口径）</li>
 *   <li>passRate：Pass@k 通过率（k 次 trial 至少 1 次达标的样本占比；k=1 时即传统通过率）</li>
 *   <li>scoreStdDev：per-trial 综合分均值的标准差（k 个 trial 均值的总体标准差；k=1 恒为 0）</li>
 *   <li>dimensionAvg：各维度均值表（key 与 Rubric 维度 key 对齐；正向分口径，
 *       hallucination 键为 1-幻觉率，值越高越安全）——门禁安全维度数据源</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskEvalSummary implements Serializable {
    private String taskId;
    private Integer trials;
    private Double passThreshold;
    /** 样本数（非结果行数；结果行数 = sampleCount × trials） */
    private Integer sampleCount;
    private Double avgOverall;
    private Double passRate;
    private Double scoreStdDev;
    /** 各维度均值（key 与 Rubric 维度 key 对齐，正向分口径；缺失 key 视为该维度未评测） */
    private Map<String, Double> dimensionAvg;
}
