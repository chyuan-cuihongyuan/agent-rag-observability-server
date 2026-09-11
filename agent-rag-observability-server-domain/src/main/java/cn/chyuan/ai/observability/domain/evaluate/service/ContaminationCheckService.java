package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.EvalDatasetItem;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 池间污染检查（工单 0173 X3+，评测卫生）— challenge/wrong 与 golden 池的 query 重叠检测，
 * 防止评测过拟合泄漏（挑战题/错题混入黄金集会让门禁结论虚高）。
 * 判定两级：归一化精确重叠 + 词面 Jaccard 相似（阈值可配）；纯函数与取数解耦。
 */
@Slf4j
@Service
public class ContaminationCheckService {

    private static final double SIMILAR_THRESHOLD = 0.8;

    private final IEvalDatasetRepository evalDatasetRepository;

    public ContaminationCheckService(IEvalDatasetRepository evalDatasetRepository) {
        this.evalDatasetRepository = evalDatasetRepository;
    }

    /** 单池全部条目的有效查询集合（归一化后） */
    public Set<String> poolQueries(String pool) {
        Set<String> queries = new LinkedHashSet<>();
        List<EvalDatasetEntity> datasets = evalDatasetRepository.queryByPool(pool, 1, 100);
        for (EvalDatasetEntity ds : datasets == null ? List.<EvalDatasetEntity>of() : datasets) {
            for (EvalDatasetItem item : parseItems(ds.getItemsJson())) {
                String q = DatasetQualityService.normalize(
                        item.getPrompt() != null && !item.getPrompt().isBlank() ? item.getPrompt() : item.getQuery());
                if (q != null) {
                    queries.add(q);
                }
            }
        }
        return queries;
    }

    /**
     * 重叠分析纯函数：exact = 精确重叠键；similar = 与池 B 任一查询 Jaccard≥0.8 的池 A 键。
     *
     * @return {overlapExact, overlapSimilar, ratioA, block}
     */
    public Map<String, Object> analyze(Set<String> queriesA, Set<String> queriesB, double blockThreshold) {
        Set<String> exact = new LinkedHashSet<>(queriesA);
        exact.retainAll(queriesB);

        Set<String> similar = new LinkedHashSet<>();
        for (String a : queriesA) {
            if (exact.contains(a)) {
                continue;
            }
            Set<String> ta = DatasetQualityService.tokens(a);
            for (String b : queriesB) {
                if (DatasetQualityService.jaccard(ta, DatasetQualityService.tokens(b)) >= SIMILAR_THRESHOLD) {
                    similar.add(a);
                    break;
                }
            }
        }

        double ratio = queriesA.isEmpty() ? 0.0
                : Math.round((double) (exact.size() + similar.size()) / queriesA.size() * 10000d) / 10000d;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queriesA", queriesA.size());
        result.put("queriesB", queriesB.size());
        result.put("overlapExact", exact.size());
        result.put("overlapSimilar", similar.size());
        result.put("ratioA", ratio);
        result.put("block", ratio > blockThreshold);
        result.put("blockThreshold", blockThreshold);
        result.put("samples", overlapSamples(exact, 20));
        return result;
    }

    private List<String> overlapSamples(Set<String> overlap, int limit) {
        return overlap.stream().limit(limit).toList();
    }

    private List<EvalDatasetItem> parseItems(String itemsJson) {
        try {
            List<EvalDatasetItem> items = JSON.parseArray(itemsJson, EvalDatasetItem.class);
            return items == null ? List.of() : items;
        } catch (Exception e) {
            return List.of();
        }
    }
}
