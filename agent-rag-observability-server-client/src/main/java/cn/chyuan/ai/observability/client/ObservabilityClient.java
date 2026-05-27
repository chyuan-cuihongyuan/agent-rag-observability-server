package cn.chyuan.ai.observability.client;

import cn.chyuan.ai.observability.client.model.AgentDecisionReport;
import cn.chyuan.ai.observability.client.model.ChatResultReport;
import cn.chyuan.ai.observability.client.model.RagRetrievalReport;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityClient {

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private ObservabilityClientConfig config;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS)
            .writeTimeout(100, TimeUnit.MILLISECONDS)
            .readTimeout(100, TimeUnit.MILLISECONDS)
            .build();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "observe-client");
        t.setDaemon(true);
        return t;
    });

    public void reportAgentDecision(AgentDecisionReport report) {
        sendAsync("decision", report);
    }

    public void reportRagRetrieval(RagRetrievalReport report) {
        sendAsync("retrieval", report);
    }

    public void reportChatResult(ChatResultReport report) {
        sendAsync("chat_result", report);
    }

    private void sendAsync(String tag, Object report) {
        executor.execute(() -> {
            try {
                String json = JSON.toJSONString(report);
                if (config.getMq().isEnabled() && rocketMQTemplate != null) {
                    String destination = config.getMq().getTopic() + ":" + tag;
                    Message<String> msg = MessageBuilder.withPayload(json)
                            .setHeader("KEYS", extractTraceId(json))
                            .build();
                    rocketMQTemplate.asyncSend(destination, msg, new org.apache.rocketmq.client.producer.SendCallback() {
                        @Override
                        public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                            log.debug("observe report sent via MQ, tag={}", tag);
                        }
                        @Override
                        public void onException(Throwable e) {
                            fallbackHttp(tag, json);
                        }
                    });
                } else if (config.getHttp().isEnabled()) {
                    fallbackHttp(tag, json);
                }
            } catch (Exception e) {
                log.debug("observe report failed: {}", e.getMessage());
            }
        });
    }

    private void fallbackHttp(String tag, String json) {
        try {
            String endpoint;
            switch (tag) {
                case "decision" -> endpoint = "/api/v1/collect/agent_decision";
                case "retrieval" -> endpoint = "/api/v1/collect/rag_retrieval";
                case "chat_result" -> endpoint = "/api/v1/collect/chat_result";
                default -> { return; }
            }
            Request request = new Request.Builder()
                    .url(config.getHttp().getUrl() + endpoint)
                    .addHeader("auth-key", config.getHttp().getAuthKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();
            httpClient.newCall(request).execute().close();
        } catch (Exception e) {
            log.debug("observe http fallback failed: {}", e.getMessage());
        }
    }

    private String extractTraceId(String json) {
        try {
            return JSON.parseObject(json).getString("traceId");
        } catch (Exception e) {
            return "";
        }
    }
}
