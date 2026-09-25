package cn.chyuan.ai.observability.domain.promqlkernel.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 时序查询端口（工单 0906 DB8，promql 思想）。
 * query 入口统一编排/与 tskernel 样本序列形态只读联动（泛型序列函数不 import）/
 * promql-kernel.enabled 默认关（开启才改变行为）。
 */
public interface PromPort {

    PromModel.Store store();

    PromModel.Series addSeries(String metric, Map<String, String> labels);

    List<PromEngine.VectorEntry> query(PromModel.Selector selector, long at, long rangeMs,
                                       boolean withRate, String aggLabel, PromEngine.AggFunc func);

    /** tskernel 只读联动形态：外部样本序列（时间戳, 值）灌入指定序列（形状数据不 import tskernel） */
    void ingestExternal(PromModel.Series target, List<long[]> timestampedValues);

    static PromPort inMemory() {
        return new InMemoryProm();
    }
}

final class InMemoryProm implements PromPort {

    private final PromModel.Store store = new PromModel.Store();

    @Override
    public PromModel.Store store() {
        return store;
    }

    @Override
    public PromModel.Series addSeries(String metric, Map<String, String> labels) {
        PromModel.Series s = new PromModel.Series(metric, labels);
        store.add(s);
        return s;
    }

    @Override
    public List<PromEngine.VectorEntry> query(PromModel.Selector selector, long at, long rangeMs,
                                              boolean withRate, String aggLabel, PromEngine.AggFunc func) {
        return PromEngine.query(store, selector, at, rangeMs, withRate, aggLabel, func);
    }

    @Override
    public void ingestExternal(PromModel.Series target, List<long[]> timestampedValues) {
        for (long[] tv : timestampedValues) {
            target.add(tv[0], tv[1]);
        }
    }
}
