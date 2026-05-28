package cn.chyuan.ai.observability.infrastructure.metrics;

import io.micrometer.core.instrument.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ObserveMetrics {

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
}
