package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM-as-judge 对一次答案的质量评判结果。各分值区间 0-1，越高越好（hallucination 越低越好）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JudgeVerdict {
    /** 忠实度：答案是否基于检索内容 */
    private double faithfulness;
    /** 相关性：答案是否回答了问题 */
    private double relevance;
    /** 幻觉比例：0-1，越低越好 */
    private double hallucinationRate;
    /** 信息完整性：答案覆盖标准答案要点的程度 */
    private double completeness;
    /** 语义相似度：答案与标准答案的语义接近度 */
    private double similarity;
    /** 答案正确性：实际答案相对标准答案的事实正确性 0-1（factual correctness，区别于 completeness 覆盖率） */
    private double answerCorrectness;
    /** 上下文精确率：检索 chunk 逐条对回答 query 是否相关，按位置加权（RAGAS Context Precision） */
    private double contextPrecision;
    /** 上下文召回率：标准答案逐句能否从检索上下文推断（RAGAS Context Recall） */
    private double contextRecall;
    /** 上下文相关性：检索内容与用户查询的整体相关度 0-1 */
    private double contextRelevance;
    /** 评判明细（原始 LLM 输出摘要，落库到 eval_detail） */
    private String detail;
    /** 是否走了降级（未配置 LLM 或调用失败） */
    private boolean degraded;
}
