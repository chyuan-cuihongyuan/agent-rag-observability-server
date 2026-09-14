package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.CandidateScorecardVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示候选评估器（工单 0325 AO3，dspy Evaluate 思想）。
 * 候选提示×评测集逐例执行（执行端口）→评分端口（异常 0 分隔离计入失败清单）
 * →聚合得分卡（均值/通过率/失败清单）。顺序确定性执行。domain 纯函数编排。
 */
public class PromptCandidateEvaluator {

    /** 评测例：输入 + 期望 */
    public record EvalCase(String input, String expected) {
    }

    /** 执行端口：候选提示 + 输入 → 实际输出（异常按失败例隔离） */
    public interface ExecPort {
        String run(String prompt, String input);
    }

    /** 评分端口：期望 vs 实际 → 0-100（异常按 0） */
    public interface ScorePort {
        double score(String expected, String actual);
    }

    private final double passLine;

    public PromptCandidateEvaluator(double passLine) {
        if (passLine < 0 || passLine > 100) {
            throw new IllegalArgumentException("通过线应在 [0,100]");
        }
        this.passLine = passLine;
    }

    /** 评估（空评测集返回零例记分卡） */
    public CandidateScorecardVO evaluate(String prompt, List<EvalCase> cases,
                                         ExecPort execPort, ScorePort scorePort) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("候选提示不能为空");
        }
        if (execPort == null || scorePort == null) {
            throw new IllegalArgumentException("执行与评分端口不能为空");
        }
        List<EvalCase> safeCases = cases == null ? List.of() : cases;
        List<String> failures = new ArrayList<>();
        double total = 0;
        int pass = 0;
        for (EvalCase evalCase : safeCases) {
            double score;
            try {
                String actual = execPort.run(prompt, evalCase.input());
                score = Math.max(0, Math.min(100, scorePort.score(evalCase.expected(), actual)));
            } catch (RuntimeException e) {
                score = 0;
            }
            total += score;
            if (score >= passLine) {
                pass++;
            } else {
                failures.add(summary(evalCase.input()) + " → " + score);
            }
        }
        int count = safeCases.size();
        return CandidateScorecardVO.builder()
                .prompt(prompt)
                .caseCount(count)
                .meanScore(count == 0 ? 0 : total / count)
                .passCount(pass)
                .passRate(count == 0 ? 0 : (double) pass / count)
                .failures(failures)
                .build();
    }

    private String summary(String input) {
        if (input == null) {
            return "null";
        }
        return input.length() <= 20 ? input : input.substring(0, 20) + "…";
    }
}
