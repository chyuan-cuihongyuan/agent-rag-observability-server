package cn.chyuan.ai.observability.api.dto.collect;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆检索追踪 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemoryRecallLogDTO {
    private String traceId;
    private String queryText;
    private Integer sessionMemoryCount;
    private Integer agentMemoryCount;
    private String sessionMemoryScores;
    private String agentMemoryScores;
    private String injectContent;
    private Integer costTimeMs;
    private String createTime;
}
