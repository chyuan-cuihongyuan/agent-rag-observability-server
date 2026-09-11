package cn.chyuan.ai.observability.infrastructure.mq.consumer;

import cn.chyuan.ai.observability.domain.dlq.service.DeadLetterService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 死信重放处理端口实现（工单 0180 Y4）— 按 tag 分派回 TraceMessageConsumer 的既有处理链路。
 */
@Slf4j
@Component
public class DeadLetterHandlerImpl implements DeadLetterService.DeadLetterHandler {

    @Resource
    private TraceMessageConsumer traceMessageConsumer;

    @Override
    public boolean handle(String topicTag, String payload) {
        try {
            // 复用消费链路：onMessage 内部按 tag 分派采集（与 MQ 消费同一入口，语义一致）
            traceMessageConsumer.onMessage(payload);
            return true;
        } catch (Exception e) {
            log.warn("死信重放处理失败: tag={}, err={}", topicTag, e.getMessage());
            return false;
        }
    }
}
