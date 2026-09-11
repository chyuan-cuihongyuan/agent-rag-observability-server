package cn.chyuan.ai.observability.infrastructure.adapter.llm;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmPairwisePort;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.PairwiseOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * pairwise 规则占位适配（工单 0170 X1）— eval.pairwise-provider 非 llm 时的默认端口实现：
 * 恒返回 null（无法判定语义），PairwiseJudgeService 据此走 overallScore 规则兜底，
 * 保证无 LLM API Key 环境下对比功能可用且测试全绿。
 * 与 LlmPairwiseAdapter（@Primary + 条件装配）二选一注入。
 */
@Component
public class RulePairwiseAdapter implements ILlmPairwisePort {

    @Override
    public PairwiseOutcome judge(String query, String answerA, String answerB) {
        return null;
    }
}
