package cn.chyuan.ai.observability.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用追踪上报模型
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolCallLogReport {
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
    /** 客户端事件时间，格式 yyyy-MM-dd HH:mm:ss；为空时服务端按接收时间补 */
    private String createTime;
}
