package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.CandidateScorecardVO;
import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.OptimResultVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 提示自动优选器（工单 0326 AO4，dspy MIPRO 简化为预算驱动）。
 * 初始候选池→评估（复用 AO3 评估器）→保留 top-k→扰动端口生成新候选→
 * 收敛（连续 N 轮无提升）/预算耗尽（评估次数上限）双出口，全程轨迹留痕。
 * domain 纯函数编排。
 */
public class PromptOptimizer {

    /** 扰动端口：当前候选 → 新候选变体列表 */
    public interface PerturbPort {
        List<String> perturb(String candidate);
    }

    private final int topK;
    private final int maxEvaluations;
    private final int patienceRounds;
    private final PromptCandidateEvaluator evaluator;

    public PromptOptimizer(PromptCandidateEvaluator evaluator, int topK,
                           int maxEvaluations, int patienceRounds) {
        if (evaluator == null) {
            throw new IllegalArgumentException("评估器不能为空");
        }
        if (topK <= 0 || maxEvaluations <= 0 || patienceRounds <= 0) {
            throw new IllegalArgumentException("topK/预算/耐心轮次必须为正数");
        }
        this.evaluator = evaluator;
        this.topK = topK;
        this.maxEvaluations = maxEvaluations;
        this.patienceRounds = patienceRounds;
    }

    public OptimResultVO optimize(List<String> initialCandidates,
                                  List<PromptCandidateEvaluator.EvalCase> cases,
                                  PromptCandidateEvaluator.ExecPort execPort,
                                  PromptCandidateEvaluator.ScorePort scorePort,
                                  PerturbPort perturbPort) {
        if (initialCandidates == null || initialCandidates.isEmpty()) {
            throw new IllegalArgumentException("初始候选池不能为空");
        }
        List<String> trajectory = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        initialCandidates.forEach(c -> seen.add(c == null ? "" : c));

        int evaluations = 0;
        double bestScore = -1;
        String best = null;
        int staleRounds = 0;
        int round = 0;
        List<String> pool = new ArrayList<>(seen);

        while (true) {
            round++;
            // 评估当前池（预算记账）
            List<Scored> scored = new ArrayList<>();
            for (String candidate : pool) {
                if (evaluations >= maxEvaluations) {
                    trajectory.add("round" + round + ":预算耗尽");
                    return exit(OptimResultVO.EXIT_BUDGET, best, bestScore, round, trajectory, evaluations);
                }
                CandidateScorecardVO card = evaluator.evaluate(candidate, cases, execPort, scorePort);
                evaluations++;
                scored.add(new Scored(candidate, card.getMeanScore()));
            }
            scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                    .thenComparing(Scored::candidate));
            double roundBest = scored.get(0).score();
            if (scored.get(0).candidate() != null && (best == null || roundBest > bestScore)) {
                best = scored.get(0).candidate();
                bestScore = roundBest;
                staleRounds = 0;
            } else {
                staleRounds++;
            }
            trajectory.add("round" + round + ":candidates=" + scored.size() + ":best=" + roundBest);
            if (staleRounds >= patienceRounds) {
                return exit(OptimResultVO.EXIT_CONVERGED, best, bestScore, round, trajectory, evaluations);
            }
            // 扰动生成新候选（排除已见）
            List<String> next = new ArrayList<>();
            if (perturbPort != null) {
                for (Scored entry : scored.subList(0, Math.min(topK, scored.size()))) {
                    for (String variant : safePerturb(perturbPort, entry.candidate())) {
                        String key = variant == null ? "" : variant;
                        if (seen.add(key)) {
                            next.add(variant);
                        }
                    }
                }
            }
            if (next.isEmpty()) {
                return exit(OptimResultVO.EXIT_EXHAUSTED, best, bestScore, round, trajectory, evaluations);
            }
            pool = next;
        }
    }

    private List<String> safePerturb(PerturbPort port, String candidate) {
        try {
            List<String> variants = port.perturb(candidate);
            return variants == null ? List.of() : variants;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private OptimResultVO exit(String reason, String best, double bestScore,
                               int rounds, List<String> trajectory, int evaluations) {
        return OptimResultVO.builder()
                .winner(best)
                .bestScore(Math.max(0, bestScore))
                .rounds(rounds)
                .exitReason(reason)
                .trajectory(trajectory)
                .evaluationsUsed(evaluations)
                .build();
    }

    private record Scored(String candidate, double score) {
    }
}
