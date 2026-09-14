package cn.chyuan.ai.observability.domain.evaluate.promptopt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测记录值对象（AO2：输入/输出/得分三元组）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalRecordVO {

    /** 输入 */
    private String input;

    /** 输出（理想答案） */
    private String output;

    /** 得分 0-100 */
    private int score;
}
