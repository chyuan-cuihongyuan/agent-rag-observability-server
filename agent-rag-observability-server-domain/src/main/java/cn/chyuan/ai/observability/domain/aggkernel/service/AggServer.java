package cn.chyuan.ai.observability.domain.aggkernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聚合分析组合实现（工单 1219 FG8，elasticsearch 思想）。
 * 文档集追加式登记，求值前绑定全集供 global 回退。
 */
public final class AggServer implements AggPort {

    private final List<Map<String, Object>> docs = new ArrayList<>();
    private final AggEngine engine = new AggEngine();

    @Override
    public void add(Map<String, Object> doc) {
        if (doc == null) {
            throw new IllegalArgumentException("文档不得为 null");
        }
        docs.add(doc);
    }

    @Override
    public List<Map<String, Object>> docs() {
        return List.copyOf(docs);
    }

    @Override
    public BucketResult run(Agg agg) {
        engine.bindRoot(docs);
        return engine.eval(agg, docs);
    }

    @Override
    public List<String> seriesShape() {
        return List.of("metric", "labels", "samples");
    }
}
