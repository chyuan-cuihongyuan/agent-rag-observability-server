package cn.chyuan.ai.observability.infrastructure.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LLM-as-Judge 专用 ChatModel 配置
 * <p>
 * 使用 DeepSeek（OpenAI 兼容）供 LlmJudgeAdapter 进行答案质量评判。
 * observability-server 未引入 spring-ai-openai-spring-boot-starter（无 auto-config），
 * 故手动创建 ChatModel bean，复用 application-dev.yml 中的 spring.ai.openai.* 配置。
 * <p>
 * Spring AI 2.0 迁移（SELFLOOP loop-20 / B08）：spring-ai-openai 基于官方 openai-java SDK 重写，
 * 原 OpenAiApi + RestClient 构造替换为 OpenAIClient（OkHttp 实现），
 * OpenAiChatModel.defaultOptions(...) 更名为 options(...)。超时语义：原 connect 30s / read 180s
 * 收敛为官方客户端全局 timeout 180s（评测调用无流式，整体上限即读上限）。
 */
@Slf4j
@Configuration
public class LlmJudgeConfig {

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:sk-xxx}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private double temperature;

    // judge 重试显式化（SELFLOOP4 loop-413，工单 0624/0625）：spring-ai 2.0.1 底层为
    // OpenAI 官方 SDK（OkHttp），重试由 maxRetries 承担（SDK 默认 2，408/429/5xx 指数退避）。
    // 显式 3 次 + 单次 180s 超时 => 最坏 4×180s ≈ 12 分钟，并发 2（O01 闸门）下可控
    @Value("${observability.eval.judge.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Bean
    @ConditionalOnProperty(name = "observability.eval.judge.enabled", havingValue = "true")
    public ChatModel chatModel() {
        log.info("初始化 LLM-Judge ChatModel: baseUrl={}, model={}", baseUrl, model);
        OpenAIClient openAIClient = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(180))
                .maxRetries(cn.chyuan.ai.observability.infrastructure.adapter.llm.JudgeRetrySupport
                        .clampMaxRetries(retryMaxAttempts))
                .build();
        return OpenAiChatModel.builder()
                .openAiClient(openAIClient)
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .build())
                .build();
    }
}
