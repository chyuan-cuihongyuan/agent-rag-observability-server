package cn.chyuan.ai.observability.domain.aggkernel.service;

import java.util.List;
import java.util.Map;

/**
 * 聚合分析端口（工单 1219 FG8，elasticsearch 思想）。
 * docs·agg 入口统一编排：指标聚合·terms 分桶·直方图·嵌套聚合·管道聚合·过滤聚合·
 * 排序截断组合管线/promqlkernel 序列形状只读联动
 * （Series: metric/labels/samples 语义名对齐，不 import promqlkernel）/
 * agg-kernel.enabled 默认关（开启才改变行为）。
 */
public interface AggPort {

    /** 追加文档（FG1） */
    void add(Map<String, Object> doc);

    /** 全量文档集（FG6 global 语义基准） */
    List<Map<String, Object>> docs();

    /** 顶层求值聚合（FG1-FG7） */
    BucketResult run(Agg agg);

    /** promqlkernel 序列形状只读联动（Series: metric/labels/samples） */
    List<String> seriesShape();

    static AggPort inMemory() {
        return new AggServer();
    }
}
