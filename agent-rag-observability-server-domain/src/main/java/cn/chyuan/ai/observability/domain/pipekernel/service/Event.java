package cn.chyuan.ai.observability.domain.pipekernel.service;

import java.util.Map;

/**
 * 事件模型（工单 0978 EF3，vector 思想）。
 * log 与 metric 事件字段/构造期校验/跨型拒绝。
 */
public record Event(String type, Map<String, String> fields) {

    public static final String LOG = "log";
    public static final String METRIC = "metric";

    /** log 事件：message 必填 */
    public static Event log(String message, Map<String, String> fields) {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("log 事件 message 必填");
        }
        var all = new java.util.LinkedHashMap<String, String>();
        all.put("message", message);
        if (fields != null) {
            all.putAll(fields);
        }
        return new Event(LOG, java.util.Collections.unmodifiableMap(all));
    }

    /** metric 事件：name 必填、value 必须数值 */
    public static Event metric(String name, double value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("metric 事件 name 必填");
        }
        return new Event(METRIC, Map.of("name", name, "value", String.valueOf(value)));
    }

    /** 跨型拒绝：要求为 log 事件 */
    public Event requireLog() {
        if (!LOG.equals(type)) {
            throw new IllegalArgumentException("跨型拒绝: 期望 log 实得 " + type);
        }
        return this;
    }

    /** 跨型拒绝：要求为 metric 事件 */
    public Event requireMetric() {
        if (!METRIC.equals(type)) {
            throw new IllegalArgumentException("跨型拒绝: 期望 metric 实得 " + type);
        }
        return this;
    }

    /** 字段读取（缺字段返回 null） */
    public String field(String name) {
        return fields.get(name);
    }
}
