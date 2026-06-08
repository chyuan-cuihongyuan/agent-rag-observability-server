package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalDatasetItem {
    private String query;
    private String standardAnswer;
    private List<String> standardChunks;

    // ========== 新增：工具调用评测字段 ==========
    /** 期望调用的工具名称列表 */
    private List<String> expectedTools;
    /** 期望的工具调用参数 */
    private Map<String, Object> expectedToolParams;

    // ========== 新增：Agent 决策评测字段 ==========
    /** 期望的意图类型 */
    private String expectedIntentType;
    /** 期望的分支类型 (RAG / DIRECT_ANSWER / TOOL_CALL) */
    private String expectedBranchType;
    /** 期望的推理步骤描述 */
    private String expectedReasoningSteps;
}
