package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.domain.observe.service.TraceQualityCalculator.TraceQuality;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceQualityCalculator 单测（SELFLOOP6 loop-678）。
 * 检索质量/忠实度/相关性的边界语义锁定。
 */
class TraceQualityCalculatorTest {

    private final TraceQualityCalculator calculator = new TraceQualityCalculator();

    private RagRetrievalEntity retrieval(String rerankScores, Integer empty, int topk, int count) {
        RagRetrievalEntity r = new RagRetrievalEntity();
        r.setRerankScores(rerankScores);
        r.setEmptyRetrieval(empty);
        r.setRetrievalTopk(topk);
        r.setRetrievalCount(count);
        return r;
    }

    private ChatResultEntity chat(String answer, String status) {
        ChatResultEntity c = new ChatResultEntity();
        c.setAnswer(answer);
        c.setFinalStatus(status);
        return c;
    }

    @Test
    @DisplayName("双输入全 null → TraceQuality 三字段全 null")
    void bothNullYieldsAllNull() {
        TraceQuality q = calculator.compute(null, null);
        assertThat(q.retrievalQuality()).isNull();
        assertThat(q.faithfulness()).isNull();
        assertThat(q.answerRelevance()).isNull();
    }

    @Test
    @DisplayName("空检索标记 → retrievalQuality = 0.0（质量最差）")
    void emptyRetrievalScoresZero() {
        TraceQuality q = calculator.compute(retrieval("[]", 1, 5, 0), null);
        assertThat(q.retrievalQuality()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("rerankScores 缺失且无 sourceDocs → null（不造假分）")
    void noSignalYieldsNull() {
        TraceQuality q = calculator.compute(retrieval("[]", 0, 5, 3), null);
        assertThat(q.retrievalQuality()).isNull();
    }

    @Test
    @DisplayName("有 rerank 分数 → 分值在 [0,1] 且 top1 占优")
    void scoredRetrievalInRange() {
        TraceQuality q = calculator.compute(retrieval("[0.9,0.8]", 0, 5, 5), null);
        assertThat(q.retrievalQuality()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("FAIL 状态 → faithfulness = 0.0")
    void failedChatFaithfulnessZero() {
        TraceQuality q = calculator.compute(retrieval("[0.8]", 0, 5, 5), chat("答案内容足够长一些", "FAIL"));
        assertThat(q.faithfulness()).isEqualTo(0.0);
    }
}
