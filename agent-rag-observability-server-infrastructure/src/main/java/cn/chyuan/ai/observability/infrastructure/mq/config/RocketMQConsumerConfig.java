package cn.chyuan.ai.observability.infrastructure.mq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RocketMQConsumerConfig {

    // RocketMQ consumer is configured via application.yml properties:
    // rocketmq.name-server, rocketmq.consumer.group
    // The @RocketMQMessageListener annotation on TraceMessageConsumer
    // handles topic, consumerGroup, and selectorExpression.
    //
    // For production tuning, adjust these in application-prod.yml:
    // - Consumer thread pool size (via rocketmq.consumer.consume-thread-min/max)
    // - Batch size for consumption
    // - Message model (CLUSTERING vs BROADCASTING)
    // - Consume timeout
}
