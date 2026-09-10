package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rubric 单维度评判结果（工单 0133 R1）：{verdict: 0|1|unknown, evidence} + 连续维度分数。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DimensionVerdict {
    /** 维度 key */
    private String key;
    /** 二元断言判定；连续分维度解析成功时不为 UNKNOWN */
    private RubricVerdict verdict;
    /** 连续分维度（binary=false）的 0-1 分数；二元断言维度为 null */
    private Double score;
    /** 评判证据（LLM 输出中的证据摘录或不可判定原因） */
    private String evidence;

    /** 该维度是否可计入综合分（unknown 的维度剔除后归一化加权） */
    public boolean isKnown() {
        return verdict != null && verdict != RubricVerdict.UNKNOWN;
    }
}
