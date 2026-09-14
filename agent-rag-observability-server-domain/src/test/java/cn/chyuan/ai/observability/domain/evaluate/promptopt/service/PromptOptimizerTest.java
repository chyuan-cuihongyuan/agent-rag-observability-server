package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.OptimResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 优选器单测（工单 0326 AO4）：top-k 保留与扰动/收敛与预算出口/轨迹。
 */
class PromptOptimizerTest {

    private static final List<PromptCandidateEvaluator.EvalCase> CASES = List.of(
            new PromptCandidateEvaluator.EvalCase("q", "A"));

    /** 确定性评分：提示含 vN 得 N*10 分 */
    private final PromptCandidateEvaluator.ScorePort scorePort =
            (expected, actual) -> actual == null ? 0 : switch (extractVersion(actual)) {
                case 1 -> 30;
                case 2 -> 60;
                case 3 -> 90;
                default -> 10;
            };

    private int extractVersion(String text) {
        for (int v = 3; v >= 1; v--) {
            if (text.contains("v" + v)) {
                return v;
            }
        }
        return 0;
    }

    @Test
    void 扰动生成循环择优() {
        PromptCandidateEvaluator evaluator = new PromptCandidateEvaluator(60);
        PromptOptimizer optimizer = new PromptOptimizer(evaluator, 2, 50, 3);
        PromptOptimizer.PerturbPort perturb = candidate -> {
            int v = extractVersion(candidate);
            return v < 3 ? List.of(candidate.replace("v" + v, "v" + (v + 1))) : List.of();
        };
        OptimResultVO result = optimizer.optimize(List.of("提示v1"), CASES,
                (prompt, input) -> prompt, scorePort, perturb);
        // v1(30) → v2(60) → v3(90) → 扰动空 → EXHAUSTED
        assertEquals("提示v3", result.getWinner());
        assertEquals(90.0, result.getBestScore(), 1e-9);
        assertEquals(OptimResultVO.EXIT_EXHAUSTED, result.getExitReason());
        assertEquals(3, result.getRounds());
        assertEquals(3, result.getEvaluationsUsed());
        assertTrue(result.getTrajectory().get(0).contains("candidates=1"));
    }

    @Test
    void 无提升收敛早停() {
        PromptCandidateEvaluator evaluator = new PromptCandidateEvaluator(60);
        PromptOptimizer optimizer = new PromptOptimizer(evaluator, 1, 50, 2);
        // 扰动恒产出相同候选（已见不再入池 → 空池）？改为扰动产出未见但同分候选
        PromptOptimizer.PerturbPort perturb = candidate ->
                List.of(candidate + "_扰动" + System.identityHashCode(candidate));
        OptimResultVO result = optimizer.optimize(List.of("固定提示"), CASES,
                (prompt, input) -> "固定输出", (e, a) -> 50, perturb);
        // 同分无提升 → 耐心 2 轮后 CONVERGED
        assertEquals(OptimResultVO.EXIT_CONVERGED, result.getExitReason());
        assertEquals("固定提示", result.getWinner());
        assertEquals(3, result.getRounds());
    }

    @Test
    void 预算耗尽出口() {
        PromptCandidateEvaluator evaluator = new PromptCandidateEvaluator(60);
        PromptOptimizer optimizer = new PromptOptimizer(evaluator, 1, 3, 5);
        PromptOptimizer.PerturbPort perturb = candidate -> List.of(candidate + "x");
        OptimResultVO result = optimizer.optimize(List.of("a"), CASES,
                (prompt, input) -> prompt, (e, a) -> 40, perturb);
        assertEquals(OptimResultVO.EXIT_BUDGET, result.getExitReason());
        assertTrue(result.getEvaluationsUsed() <= 3);
        assertTrue(result.getTrajectory().stream().anyMatch(t -> t.contains("预算耗尽")));
    }

    @Test
    void 非法输入拒绝() {
        PromptCandidateEvaluator evaluator = new PromptCandidateEvaluator(60);
        PromptOptimizer optimizer = new PromptOptimizer(evaluator, 1, 10, 1);
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(List.of(), CASES,
                (p, i) -> "x", (e, a) -> 1, null));
        assertThrows(IllegalArgumentException.class, () -> new PromptOptimizer(null, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PromptOptimizer(evaluator, 0, 1, 1));
    }
}
