package cn.chyuan.ai.observability.infrastructure.mq.consumer;

import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.domain.observe.service.ObserveCollectService;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "observability.mq.consumer", name = "enabled", havingValue = "true")
@RocketMQMessageListener(topic = "observability-trace", consumerGroup = "observability-consumer-group")
public class TraceMessageConsumer implements RocketMQListener<String> {

    @Resource
    private ObserveCollectService observeCollectService;

    @Override
    public void onMessage(String message) {
        try {
            String tag = extractTag(message);
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
                default -> log.warn("unknown trace message type: {}", tag);
            }
        } catch (Exception e) {
            log.error("consume trace message error", e);
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
        } catch (Exception ignored) {}
        return "unknown";
    }
}
