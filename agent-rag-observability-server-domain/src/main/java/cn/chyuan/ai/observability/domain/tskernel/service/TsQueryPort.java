package cn.chyuan.ai.observability.domain.tskernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 查询端口 + PromQL 子集（工单 0487 BF8，prometheus PromQL 子集思想）。
 * instant/range 查询（步长对齐 floorDiv）/聚合函数子集（sum/avg/max/min by 标签）/
 * ts-kernel.enabled 默认关。
 */
public interface TsQueryPort {

    /** instant 查询：≤ at 时刻最近一个样本点 */
    Double instant(long seriesId, long at);

    /** range 查询：[from, to] 按步长对齐取点 */
    List<TimeBlocker.Sample> range(long seriesId, long from, long to, long stepMillis);

    /** 内存假实现：样本注册表 + 步长对齐 */
    class InMemoryTsQuery implements TsQueryPort {

        private final Map<Long, TreeMap<Long, Double>> series = new java.util.concurrent.ConcurrentHashMap<>();

        /** 装载序列样本 */
        public void load(long seriesId, List<TimeBlocker.Sample> samples) {
            TreeMap<Long, Double> points = series.computeIfAbsent(seriesId, k -> new TreeMap<>());
            for (TimeBlocker.Sample sample : samples) {
                points.put(sample.timestamp(), sample.value());
            }
        }

        @Override
        public Double instant(long seriesId, long at) {
            TreeMap<Long, Double> points = series.get(seriesId);
            if (points == null) {
                return null;
            }
            Map.Entry<Long, Double> floor = points.floorEntry(at);
            return floor == null ? null : floor.getValue();
        }

        @Override
        public List<TimeBlocker.Sample> range(long seriesId, long from, long to, long stepMillis) {
            if (stepMillis <= 0) {
                throw new IllegalArgumentException("步长须 > 0: " + stepMillis);
            }
            if (from > to) {
                throw new IllegalArgumentException("范围非法 from > to");
            }
            List<TimeBlocker.Sample> result = new ArrayList<>();
            long aligned = Math.floorDiv(from, stepMillis) * stepMillis;
            for (long tick = aligned; tick <= to; tick += stepMillis) {
                Double value = instant(seriesId, Math.max(tick, from));
                if (value != null) {
                    result.add(new TimeBlocker.Sample(Math.max(tick, from), value));
                }
            }
            return result;
        }
    }

    /** 聚合函数子集：按标签值分组聚合（sum/avg/max/min） */
    static Map<String, Double> aggregateByLabels(List<Double> values, List<String> labelValues, String fn) {
        if (values.size() != labelValues.size()) {
            throw new IllegalArgumentException("值与标签等长");
        }
        Map<String, List<Double>> groups = new TreeMap<>();
        for (int i = 0; i < values.size(); i++) {
            groups.computeIfAbsent(labelValues.get(i), k -> new ArrayList<>()).add(values.get(i));
        }
        Map<String, Double> result = new TreeMap<>();
        groups.forEach((label, group) -> result.put(label, switch (fn) {
            case "sum" -> group.stream().mapToDouble(Double::doubleValue).sum();
            case "avg" -> group.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            case "max" -> group.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            case "min" -> group.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            default -> throw new IllegalArgumentException("不支持的聚合函数: " + fn);
        }));
        return result;
    }
}
