package cn.chyuan.ai.observability.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * LLM-as-Judge 专用 ChatModel 配置
 * <p>
 * 使用 DeepSeek（OpenAI 兼容）供 LlmJudgeAdapter 进行答案质量评判。
 * observability-server 未引入 spring-ai-openai-spring-boot-starter（无 auto-config），
 * 故手动创建 OpenAiChatModel bean，复用 application-dev.yml 中的 spring.ai.openai.* 配置。
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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(30000);
        requestFactory.setReadTimeout(180000);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .build())
                .build();
    }
}
