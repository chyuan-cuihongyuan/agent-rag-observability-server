package cn.chyuan.ai.observability.domain.observe.adapter.metrics;

/**
 * 观测指标端口 — domain 层定义，infrastructure 层以 Micrometer 实现（Q1）。
 * 用于解耦 domain 层对 micrometer 的直接依赖（ICachePort 同构）。
 */
public interface IMetricsPort {

    /**
     * 记录一次 LLM 调用的 token 用量（OTel GenAI 语义约定命名对齐）。
     *
     * @param inputTokens  prompt token 数（可能为 null/缺失）
     * @param outputTokens completion token 数（可能为 null/缺失）
     */
    void recordTokenUsage(Integer inputTokens, Integer outputTokens);
}
