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
public class MemoryRecallLogPO implements Serializable {
    private Long id;
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
