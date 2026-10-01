package cn.chyuan.ai.observability.domain.aggkernel.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聚合结果桶（工单 1215 FG4 嵌套载体，elasticsearch 思想）。
 * 桶键、文档计数、指标值、子桶树。
 */
public final class BucketResult {

    private final String key;
    private final long docCount;
    private final Map<String, Double> metrics = new LinkedHashMap<>();
    private final Map<String, List<BucketResult>> subBuckets = new LinkedHashMap<>();

    public BucketResult(String key, long docCount) {
        this.key = key;
        this.docCount = docCount;
    }

    public String key() {
        return key;
    }

    public long docCount() {
        return docCount;
    }

    public Map<String, Double> metrics() {
        return metrics;
    }

    public Map<String, List<BucketResult>> subBuckets() {
        return subBuckets;
    }

    public Double metric(String name) {
        return metrics.get(name);
    }

    public List<BucketResult> buckets(String name) {
        return subBuckets.getOrDefault(name, List.of());
    }

    void metric(String name, double value) {
        metrics.put(name, value);
    }

    void buckets(String name, List<BucketResult> buckets) {
        subBuckets.put(name, buckets);
    }
}
