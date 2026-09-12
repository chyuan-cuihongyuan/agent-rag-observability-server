package cn.chyuan.ai.observability.domain.observe.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;
import java.util.List;

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
    /** 会话级记忆相似度分数列表 */
    private List<Double> sessionMemoryScores;
    /** Agent 级记忆相似度分数列表 */
    private List<Double> agentMemoryScores;
    private String injectContent;
    private Integer costTimeMs;
    private String createTime;
    /** OTel GenAI 语义约定附加属性（服务端派生，ES 文档附加字段） */
    private Map<String, String> otelAttributes;
}
