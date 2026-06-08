package cn.chyuan.ai.observability.api.dto.collect;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用追踪 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolCallLogDTO {
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
}
