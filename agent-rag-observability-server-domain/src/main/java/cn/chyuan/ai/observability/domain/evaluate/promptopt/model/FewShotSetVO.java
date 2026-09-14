package cn.chyuan.ai.observability.domain.evaluate.promptopt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 少样本段值对象（AO2：成对样例 + 渲染文本）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FewShotSetVO {

    /** 入选样例（得分降序） */
    private List<EvalRecordVO> samples;

    /** 渲染为提示少样本段 */
    private String rendered;

    /** 因去重/上限被淘汰的样例数 */
    private int dropped;
}
