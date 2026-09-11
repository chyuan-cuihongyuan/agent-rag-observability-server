package cn.chyuan.ai.observability.domain.evaluate.adapter.port;

import cn.chyuan.ai.observability.domain.evaluate.model.valobj.PairwiseOutcome;

/**
 * pairwise 对比判定端口（工单 0170 X1）— LLM 实现为主；返回 null 表示无法判定，
 * 由 PairwiseJudgeService 回退规则兜底（overallScore 高者胜/相等 TIE）。
 */
public interface ILlmPairwisePort {

    /**
     * 对同一查询的两个答案做 A/B 判定。
     *
     * @return 判定结果；无法判定返回 null（调用方规则兜底），实现内不得抛出
     */
    PairwiseOutcome judge(String query, String answerA, String answerB);
}
