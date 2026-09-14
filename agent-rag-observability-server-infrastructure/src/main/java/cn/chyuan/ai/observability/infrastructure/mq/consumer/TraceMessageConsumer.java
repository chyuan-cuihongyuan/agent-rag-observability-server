package cn.chyuan.ai.observability.infrastructure.mq.consumer;

import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.MemoryRecallLogEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ToolCallLogEntity;
import cn.chyuan.ai.observability.domain.observe.service.ObserveCollectService;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "observability.mq.consumer", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${observability.mq.consumer.topic:observability-trace}",
        consumerGroup = "${observability.mq.consumer.group:observability-consumer-group}"
)
public class TraceMessageConsumer implements RocketMQListener<String> {

    private static final String MDC_TRACE_ID = "traceId";

    @Resource
    private ObserveCollectService observeCollectService;

    @Resource
    private cn.chyuan.ai.observability.infrastructure.metrics.ObserveMetrics observeMetrics;

    /** 指标缺席时 no-op（测试/禁用场景防 NPE，对齐 SessionManagementService 模式） */
    private cn.chyuan.ai.observability.infrastructure.metrics.ObserveMetrics metrics() {
        return observeMetrics != null ? observeMetrics
                : new cn.chyuan.ai.observability.infrastructure.metrics.ObserveMetrics();
    }

    @Override
    public void onMessage(String message) {
        String tag = extractTag(message);
        // 提取业务 traceId 写入 MDC，便于日志关联追踪
        String traceId = extractTraceId(message);
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(MDC_TRACE_ID, traceId);
        }
        try {
            switch (tag) {
                case "decision" -> {
                    AgentDecisionEntity entity = JSON.parseObject(message, AgentDecisionEntity.class);
                    observeCollectService.collectAgentDecision(entity);
                }
                case "retrieval" -> {
                    RagRetrievalEntity entity = JSON.parseObject(message, RagRetrievalEntity.class);
                    observeCollectService.collectRagRetrieval(entity);
                }
                case "chat_result" -> {
                    ChatResultEntity entity = JSON.parseObject(message, ChatResultEntity.class);
                    observeCollectService.collectChatResult(entity);
                }
                case "tool_call" -> {
                    ToolCallLogEntity entity = JSON.parseObject(message, ToolCallLogEntity.class);
                    observeCollectService.collectToolCallLog(entity);
                }
                case "memory_recall" -> {
                    MemoryRecallLogEntity entity = JSON.parseObject(message, MemoryRecallLogEntity.class);
                    observeCollectService.collectMemoryRecallLog(entity);
                }
                default -> log.warn("unknown trace message type: {}", tag);
            }
            metrics().recordMqConsumed("success");
        } catch (Exception e) {
            log.error("consume trace message error, tag={}, traceId={}", tag, traceId, e);
            metrics().recordMqConsumed("failure");
            // 上抛异常让 RocketMQ 感知消费失败，触发重试，超限后进入死信队列
            throw new RuntimeException(e);
        } finally {
            metrics().recordMqHeartbeat();
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private String extractTag(String message) {
        try {
            var obj = JSON.parseObject(message);
            // Primary: explicit messageType field from producer
            String type = obj.getString("messageType");
            if (type != null && !type.isEmpty()) return type;
            // Fallback: heuristic detection by unique fields
            if (obj.containsKey("intentType") || obj.containsKey("branchType")) return "decision";
            if (obj.containsKey("retrievalTopk") || obj.containsKey("retrievalCount")) return "retrieval";
            if (obj.containsKey("question") && obj.containsKey("answer")) return "chat_result";
            if (obj.containsKey("toolName") && obj.containsKey("toolOutput")) return "tool_call";
            if (obj.containsKey("sessionMemoryScores") || obj.containsKey("agentMemoryScores")) return "memory_recall";
        } catch (Exception e) {
            log.warn("解析 trace 消息 tag 失败, message={}", message != null && message.length() > 200 ? message.substring(0, 200) : message, e);
        }
        return "unknown";
    }

    private String extractTraceId(String message) {
        try {
            return JSON.parseObject(message).getString("traceId");
        } catch (Exception e) {
            log.debug("从消息中提取 traceId 失败", e);
            return "";
        }
    }
}
