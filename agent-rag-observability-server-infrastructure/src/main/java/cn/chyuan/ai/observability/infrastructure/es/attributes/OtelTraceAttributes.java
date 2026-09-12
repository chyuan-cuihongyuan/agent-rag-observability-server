package cn.chyuan.ai.observability.infrastructure.es.attributes;

/**
 * OTel GenAI 语义约定属性键（借鉴 open-telemetry/semantic-conventions GenAI spans）。
 * 附加式命名空间：原始 camelCase 字段保留，otelAttributes 键值用于跨生态互通。
 */
public final class OtelTraceAttributes {

    public static final String TRACE_ID = "trace.id";
    public static final String SPAN_ID = "span.id";
    public static final String GEN_AI_SYSTEM = "gen_ai.system";
    public static final String GEN_AI_OPERATION_NAME = "gen_ai.operation.name";
    public static final String GEN_AI_REQUEST_MODEL = "gen_ai.request.model";
    public static final String GEN_AI_CONVERSATION_ID = "gen_ai.conversation.id";
    public static final String GEN_AI_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String GEN_AI_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    public static final String TOOL_NAME = "gen_ai.tool.name";
    public static final String TOOL_CALL_DURATION = "tool.call.duration";
    public static final String ERROR_TYPE = "error.type";

    private OtelTraceAttributes() {
    }
}
