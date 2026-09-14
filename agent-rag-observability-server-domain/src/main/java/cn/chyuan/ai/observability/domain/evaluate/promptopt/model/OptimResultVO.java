package cn.chyuan.ai.observability.domain.evaluate.promptopt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 优选结果值对象（AO4：胜出提示 + 优化轨迹）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OptimResultVO {

/** 出口常量 */
public static final String EXIT_CONVERGED = "CONVERGED";
public static final String EXIT_BUDGET = "BUDGET";
public static final String EXIT_EXHAUSTED = "EXHAUSTED";

/** 胜出提示 */
private String winner;

    /** 胜出得分 */
    private double bestScore;

    /** 实际迭代轮数 */
    private int rounds;

    /** 出口：CONVERGED（无提升早停）/ BUDGET（评估预算耗尽）/ EXHAUSTED（候选池清空） */
    private String exitReason;

    /** 优化轨迹（每轮：候选数/最高分） */
    private List<String> trajectory;

    /** 评估总次数（预算记账） */
    private int evaluationsUsed;
}
