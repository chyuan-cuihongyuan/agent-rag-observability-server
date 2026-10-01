package cn.chyuan.ai.observability.domain.aggkernel.service;

import java.util.List;

/**
 * 聚合描述（工单 1212-1218 描述载体，elasticsearch 思想）。
 * Metric 指标/Terms 分桶/Histogram 直方图/Filter 过滤/Missing 缺失/Global 全集；
 * 桶聚合可挂子聚合形成嵌套树；Terms 支持排序与截断。
 */
public sealed interface Agg {

    enum Func {
        SUM, AVG, MIN, MAX, COUNT
    }

    enum Sort {
        COUNT_DESC, COUNT_ASC, KEY_ASC, KEY_DESC
    }

    record Metric(String name, Func func, String field) implements Agg {
    }

    record Terms(String name, String field, int size, Sort sort, List<Agg> subs) implements Agg {
        public Terms(String name, String field, int size, Sort sort) {
            this(name, field, size, sort, List.of());
        }
    }

    record Histogram(String name, String field, double interval, boolean minDocCount,
                     List<Agg> subs) implements Agg {
        public Histogram(String name, String field, double interval, boolean minDocCount) {
            this(name, field, interval, minDocCount, List.of());
        }
    }

    record Filter(String name, String field, String value, List<Agg> subs) implements Agg {
    }

    record Missing(String name, String field, List<Agg> subs) implements Agg {
    }

    /** 全集：无视外层过滤范围（ES global 语义） */
    record Global(String name, List<Agg> subs) implements Agg {
    }
}
