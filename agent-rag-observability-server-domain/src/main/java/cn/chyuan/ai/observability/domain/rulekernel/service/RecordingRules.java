package cn.chyuan.ai.observability.domain.rulekernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * recording rule（工单 1038 EM2，prometheus 记录规则思想）。
 * record 求值写样本序列/label 注入/缺 record 名拒绝。
 */
public final class RecordingRules {

    /** 样本：record 名 + 注入标签 + 值 + 求值 tick */
    public record Sample(String metric, Map<String, String> labels, double value, long tick) {
    }

    private final List<Sample> series = new ArrayList<>();

    /** 求值写序列：record 名与标签落样本；缺 record 名拒绝（构造侧由 RuleGroups 把关，此处双保险） */
    public synchronized void write(String record, Map<String, String> labels, double value, long tick) {
        if (record == null || record.isEmpty()) {
            throw new IllegalArgumentException("缺 record 名");
        }
        Map<String, String> injected = new TreeMap<>();
        if (labels != null) {
            injected.putAll(labels);
        }
        injected.put("job", "rulekernel");
        series.add(new Sample(record, injected, value, tick));
    }

    public synchronized List<Sample> series() {
        return List.copyOf(series);
    }

    public synchronized List<Sample> seriesOf(String record) {
        return series.stream().filter(s -> s.metric().equals(record)).toList();
    }

    public synchronized int size() {
        return series.size();
    }
}
