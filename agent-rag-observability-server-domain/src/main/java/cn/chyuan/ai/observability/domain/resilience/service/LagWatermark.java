package cn.chyuan.ai.observability.domain.resilience.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消费延迟水位分级纯函数（工单 0220 AD1，Kafka lag 生态思想）—
 * lag ≥ critical → CRITICAL；≥ warn → WARN；否则 OK。阈值配置化，脏值（负阈值）
 * 回退默认（warn 1000 / critical 10000）。
 *
 * @author chyuan
 */
public final class LagWatermark {

    public static final String LEVEL_OK = "OK";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_CRITICAL = "CRITICAL";

    public static final long DEFAULT_WARN = 1_000L;
    public static final long DEFAULT_CRITICAL = 10_000L;

    private LagWatermark() {
    }

    /** 分级判定（单 topic） */
    public static String grade(long lag, long warnThreshold, long criticalThreshold) {
        long warn = warnThreshold > 0 ? warnThreshold : DEFAULT_WARN;
        long critical = criticalThreshold > warn ? criticalThreshold : Math.max(warn, DEFAULT_CRITICAL);
        if (lag < 0) {
            lag = 0;
        }
        if (lag >= critical) {
            return LEVEL_CRITICAL;
        }
        return lag >= warn ? LEVEL_WARN : LEVEL_OK;
    }

    /** 批量分级：topic → lag 映射 → topic → level（保序） */
    public static Map<String, String> gradeAll(Map<String, Long> lagByTopic,
            long warnThreshold, long criticalThreshold) {
        Map<String, String> out = new LinkedHashMap<>();
        if (lagByTopic == null) {
            return out;
        }
        lagByTopic.forEach((topic, lag) -> out.put(topic, grade(lag == null ? 0 : lag,
                warnThreshold, criticalThreshold)));
        return out;
    }

    /** 批量最高水位（最差级别） */
    public static String worstLevel(Map<String, String> levels) {
        if (levels == null || levels.isEmpty()) {
            return LEVEL_OK;
        }
        if (levels.containsValue(LEVEL_CRITICAL)) {
            return LEVEL_CRITICAL;
        }
        return levels.containsValue(LEVEL_WARN) ? LEVEL_WARN : LEVEL_OK;
    }
}
