package cn.chyuan.ai.observability.infrastructure.es.attributes;

import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.MemoryRecallLogEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ToolCallLogEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OtelAttributeMapperTest {

    @Test
    void mapsToolCallToGenAiAttributes() {
        ToolCallLogEntity entity = ToolCallLogEntity.builder()
                .traceId("t-1").spanId("s-1").toolName("web_search")
                .costTimeMs(320).errorMessage("timeout after 300ms")
                .build();

        Map<String, String> attrs = OtelAttributeMapper.fromToolCall(entity);

        assertThat(attrs)
                .containsEntry(OtelTraceAttributes.TRACE_ID, "t-1")
                .containsEntry(OtelTraceAttributes.SPAN_ID, "s-1")
                .containsEntry(OtelTraceAttributes.TOOL_NAME, "web_search")
                .containsEntry(OtelTraceAttributes.GEN_AI_OPERATION_NAME, "execute_tool")
                .containsEntry(OtelTraceAttributes.TOOL_CALL_DURATION, "320")
                .containsEntry(OtelTraceAttributes.ERROR_TYPE, "exception");
    }

    @Test
    void mapsChatResultToGenAiAttributes() {
        ChatResultEntity entity = ChatResultEntity.builder()
                .traceId("t-2").sessionId("sess-9").modelVersion("glm-4")
                .promptTokens(128).completionTokens(64).finalStatus("success")
                .build();

        Map<String, String> attrs = OtelAttributeMapper.fromChatResult(entity);

        assertThat(attrs)
                .containsEntry(OtelTraceAttributes.GEN_AI_REQUEST_MODEL, "glm-4")
                .containsEntry(OtelTraceAttributes.GEN_AI_CONVERSATION_ID, "sess-9")
                .containsEntry(OtelTraceAttributes.GEN_AI_USAGE_INPUT_TOKENS, "128")
                .containsEntry(OtelTraceAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, "64")
                .doesNotContainKey(OtelTraceAttributes.ERROR_TYPE);
    }

    @Test
    void skipsNullFieldsForBareEntity() {
        assertThat(OtelAttributeMapper.fromToolCall(new ToolCallLogEntity()))
                .containsOnlyKeys(OtelTraceAttributes.GEN_AI_OPERATION_NAME);
        assertThat(OtelAttributeMapper.fromChatResult(new ChatResultEntity()))
                .containsOnlyKeys(OtelTraceAttributes.GEN_AI_OPERATION_NAME);
        assertThat(OtelAttributeMapper.fromRagRetrieval(new RagRetrievalEntity()))
                .containsOnlyKeys(OtelTraceAttributes.GEN_AI_OPERATION_NAME);
        assertThat(OtelAttributeMapper.fromAgentDecision(new AgentDecisionEntity()))
                .containsOnlyKeys(OtelTraceAttributes.GEN_AI_OPERATION_NAME);
        assertThat(OtelAttributeMapper.fromMemoryRecall(new MemoryRecallLogEntity()))
                .containsOnlyKeys(OtelTraceAttributes.GEN_AI_OPERATION_NAME);
    }

    @Test
    void mapsRagRetrievalAndDecisionAndMemory() {
        RagRetrievalEntity retrieval = RagRetrievalEntity.builder()
                .traceId("t-3").retrievalTopk(5).retrievalCount(5).emptyRetrieval(1).build();
        assertThat(OtelAttributeMapper.fromRagRetrieval(retrieval))
                .containsEntry("gen_ai.retrieval.topk", "5")
                .containsEntry(OtelTraceAttributes.ERROR_TYPE, "empty_retrieval");

        AgentDecisionEntity decision = AgentDecisionEntity.builder()
                .traceId("t-4").modelVersion("glm-4").intentType("qa")
                .branchType("rag").toolRetryTimes(2).build();
        assertThat(OtelAttributeMapper.fromAgentDecision(decision))
                .containsEntry(OtelTraceAttributes.GEN_AI_REQUEST_MODEL, "glm-4")
                .containsEntry("gen_ai.agent.intent", "qa")
                .containsEntry(OtelTraceAttributes.ERROR_TYPE, "tool_retry");

        MemoryRecallLogEntity memory = MemoryRecallLogEntity.builder()
                .traceId("t-5").sessionMemoryScores(java.util.List.of(0.31, 0.87)).build();
        assertThat(OtelAttributeMapper.fromMemoryRecall(memory))
                .containsEntry("gen_ai.memory.top_score", "0.87");
    }
}
