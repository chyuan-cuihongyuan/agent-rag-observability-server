package cn.chyuan.ai.observability.infrastructure.adapter.llm;

/**
 * LLM-as-judge 重试参数语义承载（SELFLOOP4 loop-413，工单 0624/0625）。
 * <p>
 * obs 的 spring-ai 2.0.1 底层为 OpenAI 官方 Java SDK（OkHttp）——重试由 SDK
 * maxRetries 承担（对 408/429/5xx 指数退避，SDK 默认 2 次），不走 spring-retry。
 * 本类只做参数钳位：[0,10]。
 */
public final class JudgeRetrySupport {

    private JudgeRetrySupport() {
    }

    /** 重试次数钳位 [0,10]：0 = SDK 默认关闭重试语义，负数视为误用归 0 */
    public static int clampMaxRetries(int maxRetries) {
        return Math.max(0, Math.min(10, maxRetries));
    }
}
