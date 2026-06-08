package cn.chyuan.ai.observability.domain.observe.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 记忆检索追踪实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemoryRecallLogEntity implements Serializable {
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
