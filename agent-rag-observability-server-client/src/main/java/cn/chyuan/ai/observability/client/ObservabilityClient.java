package cn.chyuan.ai.observability.client;

import cn.chyuan.ai.observability.client.model.AgentDecisionReport;
import cn.chyuan.ai.observability.client.model.ChatResultReport;
import cn.chyuan.ai.observability.client.model.RagRetrievalReport;
import com.alibaba.fastjson.JSON;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
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
import java.util.function.BiConsumer;

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

    private Tracer getTracer() {
        try {
            return GlobalOpenTelemetry.getTracer("observability-client", "1.0");
        } catch (Exception e) {
            return null;
        }
    }

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
            Span span = null;
            Scope scope = null;
            try {
                String json = withMessageType(tag, report);

                Tracer tracer = config.getOtel().isEnabled() ? getTracer() : null;
                if (tracer != null) {
                    span = tracer.spanBuilder("observability.report." + tag).startSpan();
                    span.setAttribute("observability.tag", tag);
                    span.setAttribute("observability.trace_id", extractTraceId(json));
                    scope = span.makeCurrent();
                }

                // W3C TraceContext propagation headers
                java.util.Map<String, String> traceHeaders = new java.util.HashMap<>();
                if (span != null) {
                    TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
                    propagator.inject(io.opentelemetry.context.Context.current(), traceHeaders, (carrier, key, value) -> carrier.put(key, value));
                }

                if (config.getMq().isEnabled() && rocketMQTemplate != null) {
                    String destination = config.getMq().getTopic() + ":" + tag;
                    MessageBuilder<String> mb = MessageBuilder.withPayload(json)
                            .setHeader("KEYS", extractTraceId(json));
                    traceHeaders.forEach((k, v) -> mb.setHeader(k, v));
                    rocketMQTemplate.asyncSend(destination, mb.build(), new org.apache.rocketmq.client.producer.SendCallback() {
                        @Override
                        public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                            log.debug("observe report sent via MQ, tag={}", tag);
                        }
                        @Override
                        public void onException(Throwable e) {
                            fallbackHttp(tag, json, traceHeaders);
                        }
                    });
                } else if (config.getHttp().isEnabled()) {
                    fallbackHttp(tag, json, traceHeaders);
                }
            } catch (Exception e) {
                log.debug("observe report failed: {}", e.getMessage());
            } finally {
                if (scope != null) scope.close();
                if (span != null) span.end();
            }
        });
    }

    private void fallbackHttp(String tag, String json, java.util.Map<String, String> traceHeaders) {
        try {
            String endpoint;
            switch (tag) {
                case "decision" -> endpoint = "/api/v1/collect/agent_decision";
                case "retrieval" -> endpoint = "/api/v1/collect/rag_retrieval";
                case "chat_result" -> endpoint = "/api/v1/collect/chat_result";
                default -> { return; }
            }
            Request.Builder rb = new Request.Builder()
                    .url(config.getHttp().getUrl() + endpoint)
                    .addHeader("auth-key", config.getHttp().getAuthKey())
                    .addHeader("Content-Type", "application/json");
            traceHeaders.forEach(rb::addHeader);
            rb.post(RequestBody.create(json, MediaType.parse("application/json")));
            httpClient.newCall(rb.build()).execute().close();
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

    private String withMessageType(String tag, Object report) {
        try {
            com.alibaba.fastjson.JSONObject object = (com.alibaba.fastjson.JSONObject) JSON.toJSON(report);
            object.put("messageType", tag);
            return object.toJSONString();
        } catch (Exception e) {
            return JSON.toJSONString(report);
        }
    }
}
