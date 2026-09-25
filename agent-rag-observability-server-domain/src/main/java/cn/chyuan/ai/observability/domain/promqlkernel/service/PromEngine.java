package cn.chyuan.ai.observability.domain.promqlkernel.service;

import cn.chyuan.ai.observability.domain.promqlkernel.service.PromModel.Sample;
import cn.chyuan.ai.observability.domain.promqlkernel.service.PromModel.Series;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询引擎（工单 0901-0905/0907 DB3-DB5·DB7，promql 思想）。
 * instant 向量 lookback 取最新/range 向量切片/rate 首尾差增速 counter 重置负差忽略/sum·avg·max by 聚合/选择→rate→聚合管线。
 */
public final class PromEngine {

    /** instant 向量结果：序列 + 最新值 */
    public record VectorEntry(Series series, long ts, double value) {
    }

    /** instant 查询：lookback 窗口 (at-lookback, at] 内取最新样本，超窗丢弃 */
    public static List<VectorEntry> instant(PromModel.Selector selector, PromModel.Store store, long at) {
        List<VectorEntry> out = new ArrayList<>();
        for (Series s : selector.select(store)) {
            Sample best = null;
            for (Sample sample : s.samples) {
                if (sample.ts() <= at && sample.ts() > at - selector.lookbackMs()
                        && (best == null || sample.ts() > best.ts())) {
                    best = sample;
                }
            }
            if (best != null) {
                out.add(new VectorEntry(s, best.ts(), best.value()));
            }
        }
        return out;
    }

    /** range 向量：[from, to] 窗口样本切片 */
    public static List<Series> range(PromModel.Selector selector, PromModel.Store store, long from, long to) {
        List<Series> out = new ArrayList<>();
        for (Series s : selector.select(store)) {
            Series window = new Series(s.metric, s.labels);
            for (Sample sample : s.samples) {
                if (sample.ts() >= from && sample.ts() <= to) {
                    window.add(sample.ts(), sample.value());
                }
            }
            if (!window.samples.isEmpty()) {
                out.add(window);
            }
        }
        return out;
    }

    /** rate：区间正增量合计 / 区间时长秒；counter 重置（后值小于前值）负差忽略 */
    public static Double rate(Series window) {
        if (window.samples.size() < 2) {
            return null;
        }
        List<Sample> samples = window.samples;
        double increase = 0;
        for (int i = 1; i < samples.size(); i++) {
            double delta = samples.get(i).value() - samples.get(i - 1).value();
            if (delta > 0) {
                increase += delta;
            }
        }
        long spanMs = samples.get(samples.size() - 1).ts() - samples.get(0).ts();
        if (spanMs <= 0) {
            return null;
        }
        return increase / (spanMs / 1000.0);
    }

    public enum AggFunc { SUM, AVG, MAX }

    /** by(label) 聚合：按标签值分组求 sum/avg/max（无该标签归入空组） */
    public static List<VectorEntry> aggregateBy(List<VectorEntry> input, String label, AggFunc func) {
        Map<String, List<VectorEntry>> groups = new LinkedHashMap<>();
        for (VectorEntry e : input) {
            String key = e.series().labels.getOrDefault(label, "");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        List<VectorEntry> out = new ArrayList<>();
        for (Map.Entry<String, List<VectorEntry>> g : groups.entrySet()) {
            double acc = switch (func) {
                case SUM, AVG -> 0;
                case MAX -> Double.NEGATIVE_INFINITY;
            };
            for (VectorEntry e : g.getValue()) {
                acc = switch (func) {
                    case SUM -> acc + e.value();
                    case AVG -> acc + e.value();
                    case MAX -> Math.max(acc, e.value());
                };
            }
            double value = func == AggFunc.AVG ? acc / g.getValue().size() : acc;
            Series result = new Series(g.getValue().get(0).series().metric,
                    g.getKey().isEmpty() ? Map.of() : Map.of(label, g.getKey()));
            result.add(g.getValue().get(0).ts(), value);
            out.add(new VectorEntry(result, g.getValue().get(0).ts(), value));
        }
        return out;
    }

    /** 查询管线（DB7）：选择 →（可选 range+rate）→（可选 by 聚合） */
    public static List<VectorEntry> query(PromModel.Store store, PromModel.Selector selector, long at,
                                          long rangeMs, boolean withRate, String aggLabel, AggFunc func) {
        List<VectorEntry> vector;
        if (withRate) {
            vector = new ArrayList<>();
            for (Series window : range(selector, store, at - rangeMs, at)) {
                Double r = rate(window);
                if (r != null) {
                    vector.add(new VectorEntry(window, at, r));
                }
            }
        } else {
            vector = instant(selector, store, at);
        }
        if (aggLabel != null) {
            vector = aggregateBy(vector, aggLabel, func == null ? AggFunc.SUM : func);
        }
        return vector;
    }
}
