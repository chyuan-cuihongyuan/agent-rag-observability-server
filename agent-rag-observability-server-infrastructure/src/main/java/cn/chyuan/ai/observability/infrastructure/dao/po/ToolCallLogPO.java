package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolCallLogPO implements Serializable {
    private Long id;
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
