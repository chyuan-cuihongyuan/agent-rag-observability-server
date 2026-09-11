package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.EvalDatasetItem;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据集质量守护（工单 0172 X3，借鉴 Evidently/Great Expectations）—
 * 重复 query 检测（归一化精确重复 + 词面 Jaccard 相似对）/ 长度分布 / 期望字段覆盖率。
 * 全部为纯函数（输入 itemsJson 反序列化后的条目列表），与端点解耦。
 */
@Slf4j
@Service
public class DatasetQualityService {

    /** 相似对判定的 Jaccard 阈值（可按需调整） */
    private static final double SIMILAR_THRESHOLD = 0.8;

    /**
     * 质量分析纯函数：返回
     * {total, exactDuplicateGroups, exactDuplicateItems, similarPairs[{a,b,jaccard}],
     *  queryLength{min,max,mean,p95}, answerCoverage, promptCoverage, traceCoverage}
     */
    public Map<String, Object> analyze(EvalDatasetEntity dataset) {
        List<EvalDatasetItem> items = parseItems(dataset == null ? null : dataset.getItemsJson());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total", items.size());
        report.putAll(exactDuplicates(items));
        report.put("similarPairs", similarPairs(items));
        report.put("queryLength", lengthDistribution(items.stream()
                .map(this::effectiveQuery)
                .filter(q -> q != null)
                .map(String::length)
                .toList()));
        report.put("answerCoverage", coverage(items, i -> i.getStandardAnswer()));
        report.put("promptCoverage", coverage(items, i -> i.getPrompt()));
        report.put("promptVersionCoverage", coverage(items, i -> i.getPromptVersion()));
        report.put("traceCoverage", coverage(items, i -> i.getTraceId()));
        return report;
    }

    /** 归一化精确重复：归一（去空白/小写）后同 query 的条目数 -1 之和与重复组数 */
    private Map<String, Object> exactDuplicates(List<EvalDatasetItem> items) {
        Map<String, Integer> count = new LinkedHashMap<>();
        for (EvalDatasetItem i : items) {
            String q = normalize(effectiveQuery(i));
            if (q != null) {
                count.merge(q, 1, Integer::sum);
            }
        }
        int dupItems = 0;
        int groups = 0;
        for (int c : count.values()) {
            if (c > 1) {
                groups++;
                dupItems += c - 1;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exactDuplicateGroups", groups);
        result.put("exactDuplicateItems", dupItems);
        return result;
    }

    /** 词面相似对（Jaccard ≥ 0.8，排除精确重复），最多返回 20 对 */
    private List<Map<String, Object>> similarPairs(List<EvalDatasetItem> items) {
        List<Map<String, Object>> pairs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size() && pairs.size() < 20; i++) {
            for (int j = i + 1; j < items.size() && pairs.size() < 20; j++) {
                Set<String> a = tokens(effectiveQuery(items.get(i)));
                Set<String> b = tokens(effectiveQuery(items.get(j)));
                if (a.isEmpty() || b.isEmpty() || a.equals(b)) {
                    continue;
                }
                double jac = jaccard(a, b);
                if (jac >= SIMILAR_THRESHOLD) {
                    String key = i + ":" + j;
                    if (seen.add(key)) {
                        Map<String, Object> pair = new LinkedHashMap<>();
                        pair.put("indexA", i);
                        pair.put("indexB", j);
                        pair.put("jaccard", Math.round(jac * 10000d) / 10000d);
                        pairs.add(pair);
                    }
                }
            }
        }
        return pairs;
    }

    /** 长度分布（min/max/mean/p95；空集全 null） */
    private Map<String, Object> lengthDistribution(List<Integer> lengths) {
        Map<String, Object> dist = new LinkedHashMap<>();
        if (lengths == null || lengths.isEmpty()) {
            dist.put("min", null);
            dist.put("max", null);
            dist.put("mean", null);
            dist.put("p95", null);
            return dist;
        }
        List<Integer> sorted = new ArrayList<>(lengths);
        sorted.sort(Integer::compareTo);
        double mean = sorted.stream().mapToInt(Integer::intValue).average().orElse(0);
        dist.put("min", sorted.get(0));
        dist.put("max", sorted.get(sorted.size() - 1));
        dist.put("mean", Math.round(mean * 100d) / 100d);
        dist.put("p95", sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1));
        return dist;
    }

    /** 字段覆盖率（非空占比；空集 0.0） */
    private double coverage(List<EvalDatasetItem> items, java.util.function.Function<EvalDatasetItem, String> field) {
        if (items.isEmpty()) {
            return 0.0;
        }
        long nonEmpty = items.stream()
                .map(field)
                .filter(v -> v != null && !v.isBlank())
                .count();
        return Math.round((double) nonEmpty / items.size() * 10000d) / 10000d;
    }

    /** 有效查询键：prompt 三元组字段优先，回退 query（与巡检种子/挖掘口径一致） */
    private String effectiveQuery(EvalDatasetItem item) {
        if (item == null) {
            return null;
        }
        return item.getPrompt() != null && !item.getPrompt().isBlank() ? item.getPrompt() : item.getQuery();
    }

    static Set<String> tokens(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        // 简单词面：按非中英文数字切分 + 中文单字（与 TraceQualityCalculator tokenize 同思路）
        for (String w : text.toLowerCase().split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            if (w.isEmpty()) {
                continue;
            }
            if (w.matches(".*[\\u4e00-\\u9fa5].*")) {
                for (char c : w.toCharArray()) {
                    if (c >= '一' && c <= '龥') {
                        tokens.add(String.valueOf(c));
                    }
                }
            } else {
                tokens.add(w);
            }
        }
        return tokens;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        return (double) inter.size() / union.size();
    }

    static String normalize(String q) {
        return q == null ? null : q.replaceAll("\\s+", "").toLowerCase();
    }

    private List<EvalDatasetItem> parseItems(String itemsJson) {
        try {
            List<EvalDatasetItem> items = JSON.parseArray(itemsJson, EvalDatasetItem.class);
            return items == null ? List.of() : items;
        } catch (Exception e) {
            log.warn("itemsJson 解析失败（按空集分析）: {}", e.getMessage());
            return List.of();
        }
    }
}
