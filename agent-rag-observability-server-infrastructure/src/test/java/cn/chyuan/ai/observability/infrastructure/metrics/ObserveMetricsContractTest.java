package cn.chyuan.ai.observability.infrastructure.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ObserveMetrics 指标契约测试（SELFLOOP2 loop-204）。
 * 指标名与 tag 是对 Grafana 面板/告警规则的对外契约——此处用 SimpleMeterRegistry
 * 断言 meter 名、tag 维度与数值累计，重构改名会立即红。
 */
@DisplayName("ObserveMetrics 指标命名契约")
class ObserveMetricsContractTest {

    private SimpleMeterRegistry registry;
    private ObserveMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ObserveMetrics();
        ReflectionTestUtils.setField(metrics, "meterRegistry", registry);
    }

    @Test
    @DisplayName("recordRequest → observe_request_total，三维 tag")
    void recordRequest_contract() {
        metrics.recordRequest("gateway", "agent-x", "main");
        assertEquals(1.0, registry.get("observe_request_total")
                .tag("source_service", "gateway")
                .tag("agent_id", "agent-x")
                .tag("branch_type", "main").counter().count());
    }

    @Test
    @DisplayName("recordAgentStatus / recordEmptyRetrieval / recordToolCall 计数契约")
    void counterContracts() {
        metrics.recordAgentStatus("success");
        metrics.recordEmptyRetrieval();
        metrics.recordToolCall("loki_search");
        metrics.recordToolCall("loki_search");

        assertEquals(1.0, registry.get("observe_agent_status_total").tag("agent_status", "success").counter().count());
        assertEquals(1.0, registry.get("observe_empty_retrieval_total").counter().count());
        assertEquals(2.0, registry.get("observe_tool_call_total").tag("tool_name", "loki_search").counter().count());
    }

    @Test
    @DisplayName("recordRequestDuration / recordRagRetrievalDuration → Timer 累计毫秒")
    void timerContracts() {
        metrics.recordRequestDuration(120);
        metrics.recordRequestDuration(80);
        metrics.recordRagRetrievalDuration(50);

        assertEquals(2, registry.get("observe_request_duration").timer().count());
        assertEquals(200.0, registry.get("observe_request_duration").timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 1e-9);
        assertEquals(50.0, registry.get("observe_rag_retrieval_duration").timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 1e-9);
    }

    @Test
    @DisplayName("recordChatResult 全量 → 计数×2 + token 双计数 + duration Timer")
    void chatResult_contract() {
        metrics.recordChatResult("agg", "success", 100, 200, 1500);

        assertEquals(1.0, registry.get("observe_chat_result_total")
                .tag("source_service", "agg").tag("final_status", "success").counter().count());
        assertEquals(100.0, registry.get("observe_chat_prompt_tokens_total")
                .tag("source_service", "agg").counter().count());
        assertEquals(200.0, registry.get("observe_chat_completion_tokens_total")
                .tag("source_service", "agg").counter().count());
        assertEquals(1, registry.get("observe_chat_result_duration").timer().count());
        assertEquals(1500.0, registry.get("observe_chat_result_duration").timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 1e-9);
    }

    @Test
    @DisplayName("recordChatResult null 入参 → unknown tag，null 计数/时长跳过不报错")
    void chatResult_nullDefaults() {
        metrics.recordChatResult(null, null, null, null, null);

        assertEquals(1.0, registry.get("observe_chat_result_total")
                .tag("source_service", "unknown").tag("final_status", "unknown").counter().count());
        // null token / duration 分支不产生对应 meter
        assertEquals(null, registry.find("observe_chat_prompt_tokens_total").counter());
        assertEquals(null, registry.find("observe_chat_result_duration").timer());
    }

    @Test
    @DisplayName("recordWriteFailure → observe_write_fail_total 按 store 维度")
    void writeFailure_contract() {
        metrics.recordWriteFailure("es");
        metrics.recordWriteFailure("es");
        metrics.recordWriteFailure("mysql");

        assertEquals(2.0, registry.get("observe_write_fail_total").tag("store", "es").counter().count());
        assertEquals(1.0, registry.get("observe_write_fail_total").tag("store", "mysql").counter().count());
    }
}
