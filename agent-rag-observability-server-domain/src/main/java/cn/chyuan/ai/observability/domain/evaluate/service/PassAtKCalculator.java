package cn.chyuan.ai.observability.domain.evaluate.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass@k 统计纯函数计算器（工单 0135 R3）— 对抗 LLM 随机性的任务级通过率与方差：
 * <ul>
 *   <li><b>样本通过口径（Pass@k 标准定义，HumanEval 同口径）</b>：样本在 k 次 trial 中
 *       <b>至少 1 次</b>综合分达标（overallScore &gt;= passThreshold）即记该样本通过；
 *       passRate = 通过样本数 / 样本总数。k=1 时退化为传统单次通过率（Pass@1），
 *       与既有行为完全一致。</li>
 *   <li><b>均值口径</b>：综合分均值 = 全部 trial 全部样本 overall 的算术平均
 *       （与既有 avg_overall_score 落库口径一致，k=1 时不变）。</li>
 *   <li><b>方差口径</b>：先算每个 trial 内全体样本的综合分均值（k 个数），再取这 k 个
 *       trial 均值的<b>总体标准差</b>（除以 k；k 个 trial 是穷尽观测而非抽样，且 k=1 时
 *       样本标准差无定义，总体口径下恒为 0，行为良好）。该值衡量「重跑一次评测，
 *       整体综合分会波动多少」，正是随机性对抗的关注点。</li>
 * </ul>
 * 无状态纯函数，便于确定性单测（构造 per-trial 分数验证公式）。
 */
public final class PassAtKCalculator {

    private PassAtKCalculator() {
    }

    /**
     * 计算 Pass@k 任务级统计。
     *
     * @param trials        试验次数 k（&gt;= 1）
     * @param passThreshold 样本达标阈值（null 视为默认 0.5，沿用 Rubric 引擎达标线）
     * @param sampleScores  每个样本的 per-trial 综合分列表（size = 样本数；内层 size = 该样本实际跑到的 trial 数，正常 = k）
     * @return passRate（通过率）与 trialMeans（各 trial 均值，k 个）
     */
    public static PassAtKResult compute(int trials, Double passThreshold, List<List<Double>> sampleScores) {
        if (trials < 1) {
            throw new IllegalArgumentException("trials 必须 >= 1: " + trials);
        }
        double threshold = passThreshold == null ? DEFAULT_PASS_THRESHOLD : passThreshold;
        int samples = sampleScores == null ? 0 : sampleScores.size();
        if (samples == 0) {
            return new PassAtKResult(0.0, new ArrayList<>());
        }

        int passed = 0;
        double sumAll = 0.0;
        int rowCount = 0;
        // 各 trial 的综合分累计（下标 = trialNo-1）；trial 数以 max(trials, 实际行) 容纳
        List<Double> trialSums = new ArrayList<>(trials);
        List<Integer> trialCounts = new ArrayList<>(trials);
        for (int i = 0; i < trials; i++) {
            trialSums.add(0.0);
            trialCounts.add(0);
        }

        for (List<Double> perTrial : sampleScores) {
            boolean samplePassed = false;
            if (perTrial != null) {
                for (int t = 0; t < perTrial.size(); t++) {
                    Double score = perTrial.get(t);
                    if (score == null) {
                        continue;
                    }
                    double v = score;
                    sumAll += v;
                    rowCount++;
                    if (t < trials) {
                        trialSums.set(t, trialSums.get(t) + v);
                        trialCounts.set(t, trialCounts.get(t) + 1);
                    }
                    // Pass@k：k 次 trial 至少 1 次达标即通过
                    if (v >= threshold) {
                        samplePassed = true;
                    }
                }
            }
            if (samplePassed) {
                passed++;
            }
        }

        List<Double> trialMeans = new ArrayList<>(trials);
        for (int t = 0; t < trials; t++) {
            int c = trialCounts.get(t);
            trialMeans.add(c == 0 ? 0.0 : trialSums.get(t) / c);
        }
        // passRate = 通过样本数 / 样本总数（分母是样本数而非结果行数）
        return new PassAtKResult((double) passed / samples, trialMeans);
    }

    /** k 个 trial 均值的总体标准差（除以 k；k=1 或空列表时为 0） */
    public static double stdDevOfTrialMeans(List<Double> trialMeans) {
        if (trialMeans == null || trialMeans.size() < 2) {
            return 0.0;
        }
        double mean = trialMeans.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = trialMeans.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average().orElse(0.0);
        return Math.sqrt(variance);
    }

    /** 便捷聚合：全部结果行综合分均值（与 avg_overall_score 同口径； rowCount=0 时 0） */
    public static double avgOfAll(List<List<Double>> sampleScores) {
        if (sampleScores == null || sampleScores.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        long count = 0;
        for (List<Double> perTrial : sampleScores) {
            if (perTrial == null) {
                continue;
            }
            for (Double v : perTrial) {
                if (v != null) {
                    sum += v;
                    count++;
                }
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    /**
     * 维度均值聚合（门禁安全维度数据源）：把每样本每 trial 的维度分累计后按行数平均。
     * key 口径与 Rubric 维度 key 一致（正向分；hallucination = 1-幻觉率）。
     */
    public static Map<String, Double> dimensionAverage(Map<String, Double> dimSums, long rowCount) {
        Map<String, Double> avg = new LinkedHashMap<>();
        if (rowCount <= 0) {
            return avg;
        }
        dimSums.forEach((k, sum) -> avg.put(k, sum / rowCount));
        return avg;
    }

    /** 样本达标阈值默认值：沿用 Rubric 引擎二元断言达标线 0.5（RubricExecutionEngine 同口径） */
    public static final double DEFAULT_PASS_THRESHOLD = 0.5;

    /** Pass@k 统计产物：passRate + 各 trial 均值 */
    public record PassAtKResult(double passRate, List<Double> trialMeans) {
    }
}
