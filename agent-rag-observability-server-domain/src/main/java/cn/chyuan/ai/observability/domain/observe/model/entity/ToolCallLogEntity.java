package cn.chyuan.ai.observability.domain.observe.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 工具调用追踪实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolCallLogEntity implements Serializable {
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String toolName;
    private String toolInput;
    private String toolOutput;
    private String status;
    private Integer costTimeMs;
    private String errorMessage;
    private Integer callOrder;
    private String createTime;
    /** OTel GenAI 语义约定附加属性（服务端派生，ES 文档附加字段） */
    private Map<String, String> otelAttributes;
}
