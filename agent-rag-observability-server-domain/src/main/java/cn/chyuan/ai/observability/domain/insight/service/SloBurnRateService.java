package cn.chyuan.ai.observability.domain.insight.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SLO 燃烧率告警服务（工单 0183 Y7，借鉴 Google SRE 多窗口燃烧率）—
 * 成功率 SLO（默认 99.9%）的错误预算 = 1 - 目标；燃烧率 = 实际错误率 / 错误预算占比。
 * 判定：快烧（1h 与 6h 同时 ≥ fast 阈值）→ PAGE；慢烧（6h 与 3d 同时 ≥ slow 阈值且非快烧）→ TICKET。
 * 纯函数与取数解耦；事件发出前走告警静默（0179）。
 */
@Slf4j
@Service
public class SloBurnRateService {

    /** 经典阈值：快烧 6、慢烧 3（SRE 手册 14.4/6 为分页口径，这里按告警分级放宽为可配） */
    @Value("${slo.target:0.999}")
    private double sloTarget = 0.999;

    @Value("${slo.fast-threshold:6}")
    private double fastThreshold = 6;

    @Value("${slo.slow-threshold:3}")
    private double slowThreshold = 3;

    /** 燃烧率纯函数：实际错误率 / 错误预算占比（(1-目标)）；目标=1 时返回无穷大的哨兵值 */
    public static double burnRate(long total, long bad, double sloTarget) {
        if (total <= 0) {
            return 0.0;
        }
        double budget = 1.0 - sloTarget;
        if (budget <= 0) {
            return bad == 0 ? 0.0 : Double.MAX_VALUE;
        }
        double errorRate = (double) bad / total;
        return errorRate / budget;
    }

    /** 分级纯函数：快烧需双短窗同时超 fast；慢烧需长窗组合同时超 slow 且非快烧 */
    public static String severity(double rate1h, double rate6h, double rate3d,
                                  double fastThreshold, double slowThreshold) {
        boolean fast = rate1h >= fastThreshold && rate6h >= fastThreshold;
        if (fast) {
            return "PAGE";
        }
        boolean slow = rate6h >= slowThreshold && rate3d >= slowThreshold;
        return slow ? "TICKET" : "NONE";
    }

    /**
     * 计算当前三级窗口燃烧率与分级（取数由仓储适配完成）。
     *
     * @param counts {w1h:[total,bad], w6h:[...], w3d:[...]}
     */
    public Map<String, Object> evaluate(Map<String, long[]> counts) {
        double r1 = burnRateOf(counts.get("1h"));
        double r6 = burnRateOf(counts.get("6h"));
        double r3 = burnRateOf(counts.get("3d"));
        String severity = severity(r1, r6, r3, fastThreshold, slowThreshold);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sloTarget", sloTarget);
        result.put("burnRate1h", round4(r1));
        result.put("burnRate6h", round4(r6));
        result.put("burnRate3d", round4(r3));
        result.put("fastThreshold", fastThreshold);
        result.put("slowThreshold", slowThreshold);
        result.put("severity", severity);
        return result;
    }

    private double burnRateOf(long[] totalAndBad) {
        return burnRateOfArr(totalAndBad, sloTarget);
    }

    static double burnRateOfArr(long[] totalAndBad, double sloTarget) {
        if (totalAndBad == null || totalAndBad.length < 2) {
            return 0.0;
        }
        return burnRate(totalAndBad[0], totalAndBad[1], sloTarget);
    }

    private double round4(double v) {
        return v == Double.MAX_VALUE ? v : Math.round(v * 10000d) / 10000d;
    }
}
