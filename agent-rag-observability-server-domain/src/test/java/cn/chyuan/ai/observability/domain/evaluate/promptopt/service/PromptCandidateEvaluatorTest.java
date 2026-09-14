package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.CandidateScorecardVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 候选评估器单测（工单 0325 AO3）：逐例执行与聚合/异常隔离/空评测集。
 */
class PromptCandidateEvaluatorTest {

    @Test
    void 逐例执行与聚合得分正确() {
        PromptCandidateEvaluator evaluator = new PromptCandidateEvaluator(60);
        List<PromptCandidateEvaluator.EvalCase> cases = List.of(
                new PromptCandidateEvaluator.EvalCase("q1", "A"),
                new PromptCandidateEvaluator.EvalCase("q2", "B"),
                new PromptCandidateEvaluator.EvalCase("q3", "C"));
        CandidateScorecardVO card = evaluator.evaluate("提示P", cases,
                (prompt, input) -> "q1".equals(input) ? "A" : "wrong",
                (expected, actual) -> expected.equals(actual) ? 100 : 20);
        assertEquals(3, card.getCaseCount());
        assertEquals((100 + 20 + 20) / 3.0, card.getMeanScore(), 1e-9);
        assertEquals(1, card.getPassCount());
        assertEquals(1.0 / 3, card.getPassRate(), 1e-9);
        assertEquals(2, card.getFailures().size());
        assertTrue(card.getFailures().get(0).contains("q2"));
    }

    @Test
    void 执行与评分异常隔离计入失败() {
        PromptCandidateEvaluator evaluator = new PromptCandidateEvaluator(60);
        CandidateScorecardVO card = evaluator.evaluate("提示P", List.of(
                        new PromptCandidateEvaluator.EvalCase("炸例", "X"),
                        new PromptCandidateEvaluator.EvalCase("好例", "Y")),
                (prompt, input) -> {
                    if ("炸例".equals(input)) {
                        throw new IllegalStateException("执行挂");
                    }
                    return "Y";
                },
                (expected, actual) -> {
                    if ("炸例".equals(actual)) {
                        throw new IllegalStateException("评分挂");
                    }
                    return 100;
                });
        assertEquals(2, card.getCaseCount());
        assertEquals(1, card.getPassCount());
        assertEquals(1, card.getFailures().size());
        assertTrue(card.getFailures().get(0).contains("炸例"));
        assertEquals(50.0, card.getMeanScore(), 1e-9);
    }

    @Test
    void 空评测集与非法输入() {
        PromptCandidateEvaluator evaluator = new PromptCandidateEvaluator(60);
        CandidateScorecardVO empty = evaluator.evaluate("提示P", List.of(),
                (prompt, input) -> "x", (expected, actual) -> 1);
        assertEquals(0, empty.getCaseCount());
        assertEquals(0.0, empty.getMeanScore());
        assertTrue(empty.getFailures().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(" ", List.of(), (p, i) -> "x", (e, a) -> 1));
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate("提示", List.of(), null, (e, a) -> 1));
        assertThrows(IllegalArgumentException.class, () -> new PromptCandidateEvaluator(101));
    }
}
