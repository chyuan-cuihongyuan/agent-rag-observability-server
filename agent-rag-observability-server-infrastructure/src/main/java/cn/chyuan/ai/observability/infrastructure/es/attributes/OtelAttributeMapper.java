package cn.chyuan.ai.observability.infrastructure.es.attributes;

import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ToolCallLogEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端派生 OTel GenAI 属性：从既有 trace 实体字段映射，null 跳过，生产端零改动。
 */
public final class OtelAttributeMapper {

    private OtelAttributeMapper() {
    }

    public static Map<String, String> fromToolCall(ToolCallLogEntity entity) {
        Map<String, String> attrs = new LinkedHashMap<>();
        put(attrs, OtelTraceAttributes.TRACE_ID, entity.getTraceId());
        put(attrs, OtelTraceAttributes.SPAN_ID, entity.getSpanId());
        put(attrs, OtelTraceAttributes.TOOL_NAME, entity.getToolName());
        put(attrs, OtelTraceAttributes.GEN_AI_OPERATION_NAME, "execute_tool");
        if (entity.getCostTimeMs() != null) {
            put(attrs, OtelTraceAttributes.TOOL_CALL_DURATION, String.valueOf(entity.getCostTimeMs()));
        }
        if (entity.getErrorMessage() != null && !entity.getErrorMessage().isEmpty()) {
            put(attrs, OtelTraceAttributes.ERROR_TYPE, "exception");
        }
        return attrs;
    }

    public static Map<String, String> fromChatResult(ChatResultEntity entity) {
        Map<String, String> attrs = new LinkedHashMap<>();
        put(attrs, OtelTraceAttributes.TRACE_ID, entity.getTraceId());
        put(attrs, OtelTraceAttributes.GEN_AI_OPERATION_NAME, "chat");
        put(attrs, OtelTraceAttributes.GEN_AI_REQUEST_MODEL, entity.getModelVersion());
        put(attrs, OtelTraceAttributes.GEN_AI_CONVERSATION_ID, entity.getSessionId());
        if (entity.getPromptTokens() != null) {
            put(attrs, OtelTraceAttributes.GEN_AI_USAGE_INPUT_TOKENS, String.valueOf(entity.getPromptTokens()));
        }
        if (entity.getCompletionTokens() != null) {
            put(attrs, OtelTraceAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, String.valueOf(entity.getCompletionTokens()));
        }
        if (entity.getFinalStatus() != null && !"success".equalsIgnoreCase(entity.getFinalStatus())) {
            put(attrs, OtelTraceAttributes.ERROR_TYPE, entity.getFinalStatus());
        }
        return attrs;
    }

    private static void put(Map<String, String> attrs, String key, String value) {
        if (value != null && !value.isEmpty()) {
            attrs.put(key, value);
        }
    }
}
