package cn.chyuan.ai.observability.domain.aggkernel.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 聚合执行引擎（工单 1212-1218，elasticsearch 思想）。
 * 指标聚合空集无值/缺失字段跳过/非数值拒绝；terms 计数排序截断；
 * 直方图左闭右开 min_doc_count 补零；嵌套子聚合递归；global 无视外层范围。
 */
public final class AggEngine {

    /** 在文档范围上求值聚合 */
    public BucketResult eval(Agg agg, List<Map<String, Object>> docs) {
        return switch (agg) {
            case Agg.Metric metric -> metric(metric, docs);
            case Agg.Terms terms -> terms(terms, docs);
            case Agg.Histogram histogram -> histogram(histogram, docs);
            case Agg.Filter filter -> filter(filter, docs);
            case Agg.Missing missing -> missing(missing, docs);
            case Agg.Global global -> global(global, docs);
        };
    }

    private BucketResult metric(Agg.Metric agg, List<Map<String, Object>> docs) {
        List<Double> values = numericValues(agg.field(), docs);
        BucketResult out = new BucketResult(agg.name(), values.size());
        if (!values.isEmpty()) {
            double value = switch (agg.func()) {
                case SUM -> values.stream().mapToDouble(Double::doubleValue).sum();
                case AVG -> values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
                case MIN -> values.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
                case MAX -> values.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                case COUNT -> values.size();
            };
            out.metric(agg.name(), value);
        }
        return out;
    }

    private BucketResult terms(Agg.Terms agg, List<Map<String, Object>> docs) {
        if (agg.size() <= 0) {
            throw new IllegalArgumentException("size 须为正: " + agg.size());
        }
        Map<String, List<Map<String, Object>>> grouped = new TreeMap<>();
        for (Map<String, Object> doc : docs) {
            Object value = doc.get(agg.field());
            if (value != null) {
                grouped.computeIfAbsent(String.valueOf(value), k -> new ArrayList<>()).add(doc);
            }
        }
        List<BucketResult> buckets = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            buckets.add(subBucket(entry.getKey(), entry.getValue(), agg.subs()));
        }
        Comparator<BucketResult> comparator = switch (agg.sort()) {
            case COUNT_DESC -> Comparator.comparingLong(BucketResult::docCount).reversed()
                    .thenComparing(BucketResult::key);
            case COUNT_ASC -> Comparator.comparingLong(BucketResult::docCount)
                    .thenComparing(BucketResult::key);
            case KEY_ASC -> Comparator.comparing(BucketResult::key);
            case KEY_DESC -> Comparator.comparing(BucketResult::key).reversed();
        };
        buckets.sort(comparator);
        BucketResult out = new BucketResult(agg.name(), docs.size());
        out.buckets(agg.name(), buckets.size() > agg.size() ? buckets.subList(0, agg.size()) : buckets);
        return out;
    }

    private BucketResult histogram(Agg.Histogram agg, List<Map<String, Object>> docs) {
        if (agg.interval() <= 0) {
            throw new IllegalArgumentException("interval 须为正: " + agg.interval());
        }
        TreeMap<Double, List<Map<String, Object>>> grouped = new TreeMap<>();
        for (Map<String, Object> doc : docs) {
            Object value = doc.get(agg.field());
            if (value instanceof Number number) {
                double v = number.doubleValue();
                double key = Math.floor(v / agg.interval()) * agg.interval();
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(doc);
            } else if (value != null) {
                throw new IllegalArgumentException("直方图字段须数值: " + agg.field());
            }
        }
        List<BucketResult> buckets = new ArrayList<>();
        appendBuckets(buckets, grouped, agg.subs());
        if (agg.minDocCount() && !grouped.isEmpty()) {
            buckets.clear();
            double first = grouped.firstKey();
            double last = grouped.lastKey();
            for (double key = first; key <= last; key += agg.interval()) {
                List<Map<String, Object>> matched = grouped.getOrDefault(key, List.of());
                buckets.add(subBucket(formatKey(key), matched, agg.subs()));
            }
        }
        BucketResult out = new BucketResult(agg.name(), docs.size());
        out.buckets(agg.name(), buckets);
        return out;
    }

    private void appendBuckets(List<BucketResult> buckets, TreeMap<Double, List<Map<String, Object>>> grouped,
                               List<Agg> subs) {
        for (Map.Entry<Double, List<Map<String, Object>>> entry : grouped.entrySet()) {
            buckets.add(subBucket(formatKey(entry.getKey()), entry.getValue(), subs));
        }
    }

    private BucketResult filter(Agg.Filter agg, List<Map<String, Object>> docs) {
        List<Map<String, Object>> matched = docs.stream()
                .filter(doc -> agg.value().equals(String.valueOf(doc.get(agg.field()))))
                .toList();
        BucketResult out = new BucketResult(agg.name(), matched.size());
        for (Agg sub : agg.subs()) {
            applySub(out, sub, matched);
        }
        return out;
    }

    private BucketResult missing(Agg.Missing agg, List<Map<String, Object>> docs) {
        List<Map<String, Object>> lacking = docs.stream()
                .filter(doc -> doc.get(agg.field()) == null)
                .toList();
        BucketResult out = new BucketResult(agg.name(), lacking.size());
        for (Agg sub : agg.subs()) {
            applySub(out, sub, lacking);
        }
        return out;
    }

    private BucketResult global(Agg.Global agg, List<Map<String, Object>> ignored) {
        List<Map<String, Object>> all = root;
        BucketResult out = new BucketResult(agg.name(), all.size());
        for (Agg sub : agg.subs()) {
            applySub(out, sub, all);
        }
        return out;
    }

    private List<Map<String, Object>> root = List.of();

    /** 顶层文档集（global 回全集用） */
    public void bindRoot(List<Map<String, Object>> docs) {
        this.root = docs;
    }

    private BucketResult subBucket(String key, List<Map<String, Object>> docs, List<Agg> subs) {
        BucketResult bucket = new BucketResult(key, docs.size());
        for (Agg sub : subs) {
            applySub(bucket, sub, docs);
        }
        return bucket;
    }

    private void applySub(BucketResult parent, Agg sub, List<Map<String, Object>> docs) {
        BucketResult result = eval(sub, docs);
        if (sub instanceof Agg.Metric metric) {
            Double value = result.metric(metric.name());
            if (value != null) {
                parent.metric(metric.name(), value);
            }
        } else if (sub instanceof Agg.Terms terms) {
            parent.buckets(terms.name(), result.buckets(terms.name()));
        } else if (sub instanceof Agg.Histogram histogram) {
            parent.buckets(histogram.name(), result.buckets(histogram.name()));
        } else {
            parent.buckets(sub instanceof Agg.Filter filter ? filter.name()
                            : sub instanceof Agg.Missing missing ? missing.name()
                            : ((Agg.Global) sub).name(),
                    List.of(result));
        }
    }

    private List<Double> numericValues(String field, List<Map<String, Object>> docs) {
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            Object value = doc.get(field);
            if (value == null) {
                continue;
            }
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("非数值字段: " + field);
            }
            values.add(number.doubleValue());
        }
        return values;
    }

    private String formatKey(double key) {
        return key == Math.rint(key) ? String.valueOf((long) key) : String.valueOf(key);
    }
}
