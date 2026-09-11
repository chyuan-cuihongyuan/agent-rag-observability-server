package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.EvalDatasetItem;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扰动鲁棒性服务（工单 0174 X5）— 生成扰动副本数据集（pool 继承 + source=perturbed 标记）
 * 与对照任务，纯函数输出两任务的 overallScore 统计与差值（稳定性口径：均值差 + 合并标准差）。
 */
@Slf4j
@Service
public class RobustnessService {

    private final PerturbationGenerator perturbationGenerator;
    private final IEvalDatasetRepository evalDatasetRepository;
    private final IEvalResultRepository evalResultRepository;

    public RobustnessService(PerturbationGenerator perturbationGenerator,
                             IEvalDatasetRepository evalDatasetRepository,
                             IEvalResultRepository evalResultRepository) {
        this.perturbationGenerator = perturbationGenerator;
        this.evalDatasetRepository = evalDatasetRepository;
        this.evalResultRepository = evalResultRepository;
    }

    /**
     * 生成扰动副本数据集：对每条目 query/prompt 应用全部扰动变体各生成一条
     * （原条目保留在最前），source=perturbed、frozen=false、版本+1。
     */
    public EvalDatasetEntity generatePerturbedDataset(String datasetId) {
        EvalDatasetEntity src = evalDatasetRepository.queryByDatasetId(datasetId);
        if (src == null) {
            throw new IllegalArgumentException("数据集不存在: " + datasetId);
        }
        List<EvalDatasetItem> srcItems = parseItems(src.getItemsJson());
        List<EvalDatasetItem> items = new ArrayList<>();
        for (EvalDatasetItem item : srcItems) {
            String base = item.getQuery() != null ? item.getQuery() : item.getPrompt();
            items.add(item);
            if (base != null) {
                for (String variant : perturbationGenerator.generate(base)) {
                    items.add(EvalDatasetItem.builder()
                            .query(variant).prompt(variant)
                            .standardAnswer(item.getStandardAnswer())
                            .traceId(item.getTraceId())
                            .build());
                }
            }
        }
        int nextVersion = evalDatasetRepository.maxVersion(src.getDatasetName()) + 1;
        EvalDatasetEntity copy = EvalDatasetEntity.builder()
                .datasetName(src.getDatasetName())
                .description(src.getDescription() == null ? "扰动副本" : src.getDescription() + "（扰动副本）")
                .itemCount(items.size())
                .itemsJson(JSON.toJSONString(items))
                .version(nextVersion)
                .pool(src.getPool())
                .source("perturbed")
                .frozen(false)
                .build();
        evalDatasetRepository.save(copy);
        return copy;
    }

    /** 单任务 overallScore 统计（count/mean/stdDev；空任务 null 统计） */
    public Map<String, Object> scoreStats(String taskId) {
        List<EvalResultEntity> results = evalResultRepository.queryByTaskId(taskId, null, 1, 1000);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("taskId", taskId);
        stats.put("count", results.size());
        if (results.isEmpty()) {
            stats.put("mean", null);
            stats.put("stdDev", null);
            return stats;
        }
        double mean = results.stream()
                .filter(r -> r.getOverallScore() != null)
                .mapToDouble(EvalResultEntity::getOverallScore)
                .average().orElse(0.0);
        double var = results.stream()
                .filter(r -> r.getOverallScore() != null)
                .mapToDouble(r -> {
                    double d = r.getOverallScore() - mean;
                    return d * d;
                })
                .average().orElse(0.0);
        stats.put("mean", round4(mean));
        stats.put("stdDev", round4(Math.sqrt(var)));
        return stats;
    }

    /** 两任务稳定性对比：均值差（扰动-原版）与各自统计 */
    public Map<String, Object> compare(String taskOriginal, String taskPerturbed) {
        Map<String, Object> a = scoreStats(taskOriginal);
        Map<String, Object> b = scoreStats(taskPerturbed);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("original", a);
        result.put("perturbed", b);
        if (a.get("mean") != null && b.get("mean") != null) {
            result.put("meanDelta", round4((Double) b.get("mean") - (Double) a.get("mean")));
        } else {
            result.put("meanDelta", null);
        }
        return result;
    }

    private List<EvalDatasetItem> parseItems(String itemsJson) {
        try {
            List<EvalDatasetItem> items = JSON.parseArray(itemsJson, EvalDatasetItem.class);
            return items == null ? List.of() : items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private double round4(double v) {
        return Math.round(v * 10000d) / 10000d;
    }
}
