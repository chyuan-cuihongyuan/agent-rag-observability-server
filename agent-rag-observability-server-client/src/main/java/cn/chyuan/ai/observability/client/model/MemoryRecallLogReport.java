package cn.chyuan.ai.observability.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆检索追踪上报模型
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemoryRecallLogReport {
    private String traceId;
    private String queryText;
    private Integer sessionMemoryCount;
    private Integer agentMemoryCount;
    private String sessionMemoryScores;
    private String agentMemoryScores;
    private String injectContent;
    private Integer costTimeMs;
    /** 客户端事件时间，格式 yyyy-MM-dd HH:mm:ss；为空时服务端按接收时间补 */
    private String createTime;
}
