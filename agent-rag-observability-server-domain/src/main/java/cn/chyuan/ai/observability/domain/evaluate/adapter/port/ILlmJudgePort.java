package cn.chyuan.ai.observability.domain.evaluate.adapter.port;

import cn.chyuan.ai.observability.domain.evaluate.model.valobj.JudgeVerdict;

import java.util.List;

/**
 * LLM-as-judge 端口 — 由 infrastructure 层用具体大模型（DeepSeek 等）实现。
 * domain 层只依赖此接口，不感知任何 AI 框架。
 */
public interface ILlmJudgePort {

    /**
     * 评判一次答案的质量，覆盖生成类 + 上下文维度指标（均为 LLM-as-Judge）：
     * <ul>
     *   <li>生成类：faithfulness（忠实度）、relevance（答案相关性）、hallucinationRate、completeness、similarity、answerCorrectness（答案正确性）</li>
     *   <li>上下文维度：contextPrecision（上下文精确率）、contextRecall（上下文召回率）、contextRelevance（上下文相关性）</li>
     * </ul>
     * 检索类确定性指标（recall/precision/f1/top3/mrr/ndcg/map）由 RetrievalMetricCalculator 纯计算，不走本端口。
     *
     * @param query           用户查询
     * @param standardAnswer  标准答案（可为空；contextRecall/answerCorrectness 依赖它）
     * @param actualAnswer    实际答案
     * @param retrievedChunks 实际检索内容（context 维度依赖它）
     * @return 评判结果；实现不可用时应返回 degraded=true 的兜底结果（新维度字段为 0）而非抛异常
     */
    JudgeVerdict judge(String query, String standardAnswer, String actualAnswer, List<String> retrievedChunks);

    /**
     * 原始文本补全（工单 0133 R1）— 输入渲染好的评判 prompt，返回 LLM 原始输出文本。
     * 供 RubricExecutionEngine 的二元断言/维度级评判使用；实现不可用或调用失败时返回 null（调用方归 unknown）。
     */
    String complete(String prompt);

    /** 当前是否具备真实 LLM 评判能力（false 表示仅降级） */
    boolean available();
}
