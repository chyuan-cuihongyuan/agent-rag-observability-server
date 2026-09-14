package cn.chyuan.ai.observability.infrastructure.metrics;

import io.micrometer.core.instrument.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class ObserveMetrics {

    private final java.util.concurrent.atomic.AtomicLong mqLastConsumedEpochMs = new java.util.concurrent.atomic.AtomicLong();

    @Resource
    private MeterRegistry meterRegistry;

    private Counter requestTotal;
    private Counter agentStatusTotal;
    private Counter emptyRetrievalTotal;
    private Counter toolCallTotal;
    private Timer requestDuration;
    private Timer ragRetrievalDuration;

    public void recordRequest(String sourceService, String agentId, String branchType) {
        Counter.builder("observe_request_total")
                .tag("source_service", sourceService)
                .tag("agent_id", agentId)
                .tag("branch_type", branchType)
                .register(meterRegistry).increment();
    }

    public void recordAgentStatus(String agentStatus) {
        Counter.builder("observe_agent_status_total")
                .tag("agent_status", agentStatus)
                .register(meterRegistry).increment();
    }

    public void recordEmptyRetrieval() {
        Counter.builder("observe_empty_retrieval_total")
                .register(meterRegistry).increment();
    }

    public void recordToolCall(String toolName) {
        Counter.builder("observe_tool_call_total")
                .tag("tool_name", toolName)
                .register(meterRegistry).increment();
    }

    public void recordRequestDuration(long durationMs) {
        Timer.builder("observe_request_duration")
                .register(meterRegistry).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordRagRetrievalDuration(long durationMs) {
        Timer.builder("observe_rag_retrieval_duration")
                .register(meterRegistry).record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录问答结果指标：状态计数 + Token 累计 + 耗时分布
     * 此前 chat_result 链路完全无 Prometheus 指标，Token/耗时/状态对告警不可见
     */
    public void recordChatResult(String sourceService, String finalStatus,
                                 Integer promptTokens, Integer completionTokens, Integer totalCostTimeMs) {
        String service = sourceService == null ? "unknown" : sourceService;
        String status = finalStatus == null ? "unknown" : finalStatus;
        Counter.builder("observe_chat_result_total")
                .tag("source_service", service)
                .tag("final_status", status)
                .register(meterRegistry).increment();
        if (promptTokens != null) {
            Counter.builder("observe_chat_prompt_tokens_total")
                    .tag("source_service", service)
                    .register(meterRegistry).increment(promptTokens);
        }
        if (completionTokens != null) {
            Counter.builder("observe_chat_completion_tokens_total")
                    .tag("source_service", service)
                    .register(meterRegistry).increment(completionTokens);
        }
        if (totalCostTimeMs != null) {
            Timer.builder("observe_chat_result_duration")
                    .tag("source_service", service)
                    .register(meterRegistry).record(totalCostTimeMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 记录写入失败（ES / MySQL / 线程池拒绝等）
     */
    /** MQ 消费计数（SELFLOOP3 loop-336，工单 0470/0471）：outcome=success/failure；registry 缺席时 no-op */
    public void recordMqConsumed(String outcome) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("observe_mq_consumed_total")
                .tag("outcome", outcome == null ? "unknown" : outcome)
                .register(meterRegistry).increment();
    }

    /** MQ 最近消费时间戳（epoch ms）：消费停摆告警的锚点（now - last 持续增大即停摆）；registry 缺席时仅推进时间戳 */
    public void recordMqHeartbeat() {
        if (meterRegistry != null) {
            Gauge.builder("observe_mq_last_consumed_epoch_ms",
                            mqLastConsumedEpochMs, AtomicLong::get)
                    .register(meterRegistry);
        }
        mqLastConsumedEpochMs.set(System.currentTimeMillis());
    }

    public void recordWriteFailure(String store) {
        Counter.builder("observe_write_fail_total")
                .tag("store", store)
                .register(meterRegistry).increment();
    }
}
