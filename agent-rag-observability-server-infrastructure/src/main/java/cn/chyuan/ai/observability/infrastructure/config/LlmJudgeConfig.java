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
 * 故手动创建 OpenAiChatModel bean，复用 application-dev.yml 中的 spring.ai.openai.* 配置。
 * <p>
 * Spring AI 2.0 起基于官方 openai-java SDK（工单 0024）：客户端固定在 baseUrl 后拼
 * /chat/completions——DeepSeek 的 /chat/completions 与旧默认 /v1/chat/completions 均为
 * 官方有效路径，行为等价；原 RestClient 超时定制由 SDK timeout() 整体超时替代。
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

    @Bean
    @ConditionalOnProperty(name = "observability.eval.judge.enabled", havingValue = "true")
    public ChatModel chatModel() {
        log.info("初始化 LLM-Judge ChatModel: baseUrl={}, model={}", baseUrl, model);
        OpenAIClient openAIClient = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .timeout(Duration.ofMillis(180000))
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
