package cn.chyuan.ai.observability.domain.evaluate.promptopt.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.OptimExperimentVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 优化实验内存仓储（AO7 默认实现；本机无中间件环境测试与运行兜底）。
 */
public class InMemoryOptimExperimentRepository implements OptimExperimentRepository {

    private final Map<String, OptimExperimentVO> store = new LinkedHashMap<>();

    @Override
    public synchronized void save(OptimExperimentVO experiment) {
        if (experiment == null || experiment.getExperimentId() == null
                || experiment.getExperimentId().isBlank()) {
            throw new IllegalArgumentException("实验ID不能为空");
        }
        store.put(experiment.getExperimentId(), experiment);
    }

    @Override
    public synchronized Optional<OptimExperimentVO> findById(String experimentId) {
        return Optional.ofNullable(store.get(experimentId));
    }

    @Override
    public synchronized List<OptimExperimentVO> listAll() {
        return List.copyOf(store.values());
    }

    @Override
    public synchronized Optional<CurveCompareVO> compare(String leftId, String rightId) {
        OptimExperimentVO left = store.get(leftId);
        OptimExperimentVO right = store.get(rightId);
        if (left == null || right == null) {
            return Optional.empty();
        }
        return Optional.of(new CurveCompareVO(
                leftId, rightId,
                left.getScoreCurveJson(), right.getScoreCurveJson(),
                bestOf(left.getScoreCurveJson()), bestOf(right.getScoreCurveJson()),
                bestOf(right.getScoreCurveJson()) - bestOf(left.getScoreCurveJson())));
    }

    /** 曲线 JSON 解析最高分（[0.6,0.8,...] 形式，坏 JSON 按 0） */
    public static double bestOf(String curveJson) {
        if (curveJson == null || curveJson.isBlank()) {
            return 0;
        }
        try {
            String body = curveJson.replace("[", "").replace("]", "").trim();
            if (body.isEmpty()) {
                return 0;
            }
            double best = 0;
            for (String part : body.split(",")) {
                best = Math.max(best, Double.parseDouble(part.trim()));
            }
            return best;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 按创建时间排序的副本（查询辅助） */
    public List<OptimExperimentVO> listByCreatedAsc() {
        List<OptimExperimentVO> out = new ArrayList<>(store.values());
        out.sort(Comparator.comparingLong(OptimExperimentVO::getCreatedAtMs));
        return out;
    }
}
