package cn.chyuan.ai.observability.trigger.rocketmq;

import cn.chyuan.ai.observability.infrastructure.observability.TraceContextPropagator;
import io.opentelemetry.context.Context;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RocketMQMessageListener(topic = "observability-trace", consumerGroup = "observability-consumer-group")
public class TraceAwareConsumer implements RocketMQListener<String> {

    @Autowired
    private TraceContextPropagator propagator;

    @Override
    public void onMessage(String message) {
        Map<String, String> headers = new HashMap<>();
        // 从 RocketMQ Message 中提取 traceparent
        // TODO: 从实际 Message 对象中提取 headers
        Context context = propagator.extract(Context.current(), headers);

        // 在恢复的 Context 中执行业务逻辑
        try (var scope = context.makeCurrent()) {
            processMessage(message);
        }
    }

    private void processMessage(String message) {
        // 业务处理逻辑
    }
}
