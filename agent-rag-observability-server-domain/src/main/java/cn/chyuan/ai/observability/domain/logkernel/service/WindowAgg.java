package cn.chyuan.ai.observability.domain.logkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 时间窗聚合（工单 0577 BQ5，loki 管道聚合思想）。
 * 窗口对齐（epoch 向下取整）/count-sum-avg 聚合（数值字段提取）/
 * group by 标签分组/空窗补零可选/结果按窗口升序。纯函数。
 */
public final class WindowAgg {

    public enum Func {
        COUNT, SUM, AVG
    }

    /** 聚合行（窗口起点 + 分组值 + 聚合值） */
    public record AggRow(long windowStart, String group, double value) {
    }

    private final long windowMillis;

    public WindowAgg(long windowMillis) {
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("窗口须为正");
        }
        this.windowMillis = windowMillis;
    }

    public long windowMillis() {
        return windowMillis;
    }

    /** 窗口对齐起点 */
    public long align(long ts) {
        return Math.floorDiv(ts, windowMillis) * windowMillis;
    }

    /**
     * 聚合：输入为（时间戳，分组值，数值字段）三元组；字段缺失跳过；
     * groupKey=null 全局单组；fillGaps 补零窗口 [最小窗, 最大窗]。
     */
    public List<AggRow> aggregate(List<Measurement> measurements, Func func, String groupKey,
            boolean fillGaps) {
        TreeMap<Long, TreeMap<String, List<Double>>> buckets = new TreeMap<>();
        long minWindow = Long.MAX_VALUE;
        long maxWindow = Long.MIN_VALUE;
        for (Measurement measurement : measurements) {
            long window = align(measurement.ts());
            String group = groupKey == null ? "" : measurement.group();
            buckets.computeIfAbsent(window, w -> new TreeMap<>())
                    .computeIfAbsent(group, g -> new ArrayList<>())
                    .add(measurement.value());
            minWindow = Math.min(minWindow, window);
            maxWindow = Math.max(maxWindow, window);
        }
        List<AggRow> rows = new ArrayList<>();
        if (buckets.isEmpty()) {
            return List.of();
        }
        for (Map.Entry<Long, TreeMap<String, List<Double>>> windowEntry : buckets.entrySet()) {
            for (Map.Entry<String, List<Double>> groupEntry : windowEntry.getValue().entrySet()) {
                rows.add(new AggRow(windowEntry.getKey(), groupEntry.getKey(),
                        reduce(func, groupEntry.getValue())));
            }
        }
        if (fillGaps && groupKey != null) {
            fillZeroWindows(rows, buckets, minWindow, maxWindow);
        }
        rows.sort((a, b) -> a.windowStart() != b.windowStart()
                ? Long.compare(a.windowStart(), b.windowStart())
                : a.group().compareTo(b.group()));
        return List.copyOf(rows);
    }

    private void fillZeroWindows(List<AggRow> rows, TreeMap<Long, TreeMap<String, List<Double>>> buckets,
            long minWindow, long maxWindow) {
        java.util.Set<String> groups = new java.util.TreeSet<>();
        buckets.values().forEach(m -> groups.addAll(m.keySet()));
        for (String group : groups) {
            for (long window = minWindow; window <= maxWindow; window += windowMillis) {
                final long start = window;
                boolean present = rows.stream()
                        .anyMatch(r -> r.windowStart() == start && r.group().equals(group));
                if (!present) {
                    rows.add(new AggRow(start, group, 0.0d));
                }
            }
        }
    }

    private double reduce(Func func, List<Double> values) {
        return switch (func) {
            case COUNT -> values.size();
            case SUM -> values.stream().mapToDouble(Double::doubleValue).sum();
            case AVG -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        };
    }

    /** 度量三元组 */
    public record Measurement(long ts, String group, double value) {
    }

    /** 从解析行提取数值度量（字段缺失/非数值返回 null 由调用方跳过） */
    public static Measurement measurement(ParserStages.ParsedLine parsed, long ts, String group,
            String valueField) {
        String raw = parsed.extracted().get(valueField);
        if (raw == null) {
            return null;
        }
        try {
            return new Measurement(ts, group, Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 批量提取（跳过 null） */
    public static List<Measurement> measurements(List<ParserStages.ParsedLine> lines, List<Long> timestamps,
            String group, String valueField) {
        List<Measurement> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Measurement measurement = measurement(lines.get(i), timestamps.get(i), group, valueField);
            if (measurement != null) {
                out.add(measurement);
            }
        }
        return out;
    }

    /** 聚合行映射（供组合管线输出，保持键序） */
    public Map<Long, Double> byWindow(List<AggRow> rows) {
        Map<Long, Double> out = new LinkedHashMap<>();
        for (AggRow row : rows) {
            out.put(row.windowStart(), row.value());
        }
        return out;
    }
}
