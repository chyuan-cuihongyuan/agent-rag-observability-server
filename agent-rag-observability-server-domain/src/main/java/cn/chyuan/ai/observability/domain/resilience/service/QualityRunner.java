package cn.chyuan.ai.observability.domain.resilience.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据质量断言运行器纯函数（工单 0221 AD2，借鉴 Great Expectations）—
 * 对行集（内存构造，真实数据面经行集装配端口接入）求值规则：
 * NOT_NULL 非空非空白；RANGE 数值在 [min,max]；ENUM 值在集合；FRESHNESS
 * 时间字段（epoch 毫秒）距 now 不超 maxAgeMs。失败样本留前 5 条，失败不中断后续行。
 *
 * @author chyuan
 */
public final class QualityRunner {

    /** 校验结果 */
    public record QualityResult(String ruleName, boolean pass, int checked, int violated,
            List<String> failureSamples, long ranAt) {

        public static QualityResult empty(String ruleName, long now) {
            return new QualityResult(ruleName, true, 0, 0, List.of(), now);
        }

        public Map<String, Object> toMap() {
            return Map.of("ruleName", ruleName, "pass", pass, "checked", checked,
                    "violated", violated, "samples", failureSamples);
        }
    }

    private static final int MAX_SAMPLES = 5;

    private QualityRunner() {
    }

    /** 求值单规则：rows 每行 = 字段名 → 值 */
    public static QualityResult evaluate(QualityRule rule, List<Map<String, Object>> rows, long nowMs) {
        int violated = 0;
        int checked = 0;
        List<String> samples = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                checked++;
                String violation = checkRow(rule, row, nowMs);
                if (violation != null) {
                    violated++;
                    if (samples.size() < MAX_SAMPLES) {
                        samples.add(violation);
                    }
                }
            }
        }
        return new QualityResult(rule.name(), violated == 0, checked, violated,
                List.copyOf(samples), nowMs);
    }

    /** 单行判定：返回违规描述（合规返回 null） */
    static String checkRow(QualityRule rule, Map<String, Object> row, long nowMs) {
        Object value = row == null ? null : row.get(rule.field());
        return switch (rule.type()) {
            case QualityRule.TYPE_NOT_NULL -> isBlank(value) ? rule.field() + " 为空" : null;
            case QualityRule.TYPE_RANGE -> inRange(rule, value) ? null
                    : rule.field() + " 超出范围: " + value;
            case QualityRule.TYPE_ENUM -> rule.enumValues().contains(String.valueOf(value)) ? null
                    : rule.field() + " 不在枚举: " + value;
            case QualityRule.TYPE_FRESHNESS -> isFresh(rule, value, nowMs) ? null
                    : rule.field() + " 数据过期: " + value;
            default -> "未知断言类型";
        };
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static boolean inRange(QualityRule rule, Object value) {
        if (value instanceof Number number) {
            double v = number.doubleValue();
            double[] bounds = rule.rangeBounds();
            return v >= bounds[0] && v <= bounds[1];
        }
        try {
            double v = Double.parseDouble(String.valueOf(value));
            double[] bounds = rule.rangeBounds();
            return v >= bounds[0] && v <= bounds[1];
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isFresh(QualityRule rule, Object value, long nowMs) {
        long ts;
        if (value instanceof Number number) {
            ts = number.longValue();
        } else {
            try {
                ts = Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return nowMs - ts <= rule.maxAgeMs();
    }
}
