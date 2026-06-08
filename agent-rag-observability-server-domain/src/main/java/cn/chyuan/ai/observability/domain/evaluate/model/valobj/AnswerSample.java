package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 一次评测样本的实际产出 — 由答案来源 Provider 提供（在线回放上游 Agent 或离线复用已采集 Trace）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnswerSample {
    /** 关联的 traceId（在线回放为新生成，离线复用为命中的历史 trace） */
    private String traceId;
    /** 实际生成的答案 */
    private String actualAnswer;
    /** 实际检索到的内容片段（用于召回/精确率与忠实度评判） */
    private List<String> retrievedChunks;

    // ========== 新增：工具调用评测字段 ==========
    /** 实际调用的工具名称列表 */
    private List<String> toolCalls;
    /** 实际的工具调用参数 */
    private Map<String, Object> toolParams;

    // ========== 新增：Agent 决策评测字段 ==========
    /** 实际的意图类型 */
    private String intentType;
    /** 实际的分支类型 */
    private String branchType;
    /** 实际的推理步骤 */
    private String reasoningSteps;
}
