package cn.chyuan.ai.observability.domain.tskernel.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 标签索引（工单 0482 BF3，prometheus index 思想）。
 * 标签键值倒排/series selector 等值交集匹配/序列 id 稳定分配（字典序签名确定性）。
 */
public class LabelIndex {

    /** 序列：指标名 + 有序标签集 */
    public record Series(long seriesId, String metric, java.util.SortedMap<String, String> labels) {
    }

    /** selector：一组等值匹配（label=value） */
    public record Selector(java.util.Map<String, String> equalsMatch) {
    }

    private final Map<String, Long> signatureToId = new HashMap<>();
    private final Map<Long, Series> seriesById = new java.util.LinkedHashMap<>();
    /** 倒排：label → value → seriesIds */
    private final Map<String, Map<String, Set<Long>>> inverted = new HashMap<>();
    private long nextSeriesId;

    /** 注册序列：签名（metric + 字典序标签）相同幂等返回同 id */
    public synchronized long register(String metric, Map<String, String> labels) {
        String signature = signature(metric, labels);
        Long existing = signatureToId.get(signature);
        if (existing != null) {
            return existing;
        }
        long id = ++nextSeriesId;
        java.util.SortedMap<String, String> sorted = new TreeMap<>(labels);
        seriesById.put(id, new Series(id, metric, java.util.Collections.unmodifiableSortedMap(sorted)));
        signatureToId.put(signature, id);
        for (Map.Entry<String, String> label : sorted.entrySet()) {
            inverted.computeIfAbsent(label.getKey(), k -> new HashMap<>())
                    .computeIfAbsent(label.getValue(), v -> new LinkedHashSet<>())
                    .add(id);
        }
        return id;
    }

    /** 等值匹配：各标签倒排取交集（结果按 seriesId 升序） */
    public synchronized List<Series> select(String metric, Selector selector) {
        Set<Long> candidates = null;
        for (Map.Entry<String, String> match : selector.equalsMatch().entrySet()) {
            Set<Long> posting = inverted.getOrDefault(match.getKey(), Map.of())
                    .getOrDefault(match.getValue(), Set.of());
            if (candidates == null) {
                candidates = new LinkedHashSet<>(posting);
            } else {
                candidates.retainAll(posting);
            }
            if (candidates.isEmpty()) {
                return List.of();
            }
        }
        List<Series> result = new ArrayList<>();
        for (Series series : seriesById.values()) {
            if (!series.metric().equals(metric)) {
                continue;
            }
            if (candidates != null && !candidates.contains(series.seriesId())) {
                continue;
            }
            result.add(series);
        }
        result.sort(java.util.Comparator.comparingLong(Series::seriesId));
        return result;
    }

    public synchronized Series series(long seriesId) {
        return seriesById.get(seriesId);
    }

    public synchronized int seriesCount() {
        return seriesById.size();
    }

    /** 确定性签名：metric + 标签字典序 */
    static String signature(String metric, Map<String, String> labels) {
        StringBuilder canonical = new StringBuilder(metric).append('{');
        new TreeMap<>(labels).forEach((key, value) -> canonical.append(key).append('=').append(value).append(','));
        return canonical.append('}').toString();
    }
}
