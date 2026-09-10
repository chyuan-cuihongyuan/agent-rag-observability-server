package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.GateDimensionKeys;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.TaskEvalSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 门禁判定引擎（工单 0136 R4）— domain 纯函数：输入任务级评测汇总 + Gate 配置 → 结论 + 触发明细。
 * <p>
 * 分层判定规则（优先级从高到低）：
 * <ol>
 *   <li><b>安全一票否决</b>：safetyDims 任一维度实际值 < 下限（缺失维度按 0 计并注明「维度缺失」，
 *       安全口径从严）→ BLOCK。即使总分/分数阈值全部达标，只要有安全越限结论即为 BLOCK。</li>
 *   <li><b>分数阈值</b>：scoreThresholds 任一 metric 实际值 < 下限 → BLOCK（附触发明细）。
 *       metric 取值：overall → avgOverall；passRate → passRate；其余 → dimensionAvg 对应维度。</li>
 *   <li>全部达标 → PASS。</li>
 * </ol>
 * <b>阈值边界口径</b>：等于阈值视为达标（safety: value &gt;= limit 达标，越限是严格小于；
 * score: value &gt;= min 达标）——两侧统一「闭区间下限」，杜绝浮点边界歧义。
 * <p>
 * 明细收集口径：安全与分数的触发明细全部收集后统一返回（利于排查），结论由
 * 「任一安全越限 或 任一分数越限」决定；一票否决体现为安全越限单独即可 BLOCK。
 */
@Service
public class GateDecisionEngine {

    /** 门禁结论常量 */
    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_BLOCK = "BLOCK";

    /**
     * 执行门禁判定（纯函数，无副作用）。
     *
     * @param summary 任务级评测汇总（含各维度均值与 Pass@k 结果）；null 视为任务失败
     * @param gate    门禁规则（safetyDims/scoreThresholds 已解析）
     */
    public GateDecision judge(TaskEvalSummary summary, GateEntity gate) {
        if (gate == null) {
            throw new IllegalArgumentException("gate 不能为空");
        }
        // 任务失败/无汇总：无法判定，门控口径从严按 BLOCK（明细注明原因，防止回测挂掉静默放行）
        if (summary == null) {
            return new GateDecision(RESULT_BLOCK, List.of(GateTrigger.taskFailed(
                    "评测任务执行失败或无汇总数据，门禁无法判定，按 BLOCK 处理")));
        }

        List<GateTrigger> triggers = new ArrayList<>();
        Map<String, Double> dims = summary.getDimensionAvg() == null ? Map.of() : summary.getDimensionAvg();

        // 1. 安全维度一票否决（先判安全：缺失维度按 0 计，从严）
        Map<String, Double> safety = gate.getSafetyDims() == null ? Map.of() : gate.getSafetyDims();
        for (Map.Entry<String, Double> rule : safety.entrySet()) {
            String dim = rule.getKey();
            double limit = rule.getValue() == null ? 0.0 : rule.getValue();
            Double actual = dims.get(dim);
            double value = actual == null ? 0.0 : actual;
            if (value < limit) {
                triggers.add(GateTrigger.safety(dim, value, limit,
                        actual == null ? "维度缺失按 0 计（安全口径从严）" : "安全维度低于下限，一票否决"));
            }
        }

        // 2. 分数阈值逐项比对
        Map<String, Double> thresholds = gate.getScoreThresholds() == null ? Map.of() : gate.getScoreThresholds();
        for (Map.Entry<String, Double> rule : thresholds.entrySet()) {
            String metric = rule.getKey();
            double min = rule.getValue() == null ? 0.0 : rule.getValue();
            Double actual = resolveMetric(summary, dims, metric);
            double value = actual == null ? 0.0 : actual;
            if (value < min) {
                triggers.add(GateTrigger.score(metric, value, min,
                        actual == null ? "指标缺失按 0 计" : "分数低于阈值"));
            }
        }

        return triggers.isEmpty() ? new GateDecision(RESULT_PASS, triggers) : new GateDecision(RESULT_BLOCK, triggers);
    }

    /** metric → 实际值：overall/passRate 走汇总字段，其余走维度均值表（缺失返回 null） */
    private Double resolveMetric(TaskEvalSummary summary, Map<String, Double> dims, String metric) {
        if (GateDimensionKeys.OVERALL.equals(metric)) {
            return summary.getAvgOverall();
        }
        if (GateDimensionKeys.PASS_RATE.equals(metric)) {
            return summary.getPassRate();
        }
        return dims.get(metric);
    }

    /** 门禁判定结论 */
    public record GateDecision(String result, List<GateTrigger> triggers) {
        public boolean isPass() {
            return RESULT_PASS.equals(result);
        }
    }

    /** 触发明细（落 eval_gate_record.trigger_detail JSON 数组的元素结构） */
    public record GateTrigger(String ruleType, String dim, Double actual, Double threshold, String note) {
        static GateTrigger safety(String dim, double actual, double threshold, String note) {
            return new GateTrigger("SAFETY", dim, actual, threshold, note);
        }

        static GateTrigger score(String metric, double actual, double threshold, String note) {
            return new GateTrigger("SCORE", metric, actual, threshold, note);
        }

        static GateTrigger taskFailed(String note) {
            return new GateTrigger("TASK_FAILED", "task", null, null, note);
        }
    }
}
