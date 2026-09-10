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

    // ========== 新增：Task 三元组字段（工单 0134 R2） ==========
    /** 样本绑定的原始 prompt 文本（三元组之一：prompt 引用，Agent 评测对齐线上真实输入） */
    private String prompt;
    /** prompt 版本（与 prompt 配套，区分同一模板的迭代） */
    private String promptVersion;
    /** 来源 trace 锚点（三元组之一：traceId 关联，回填时指向沉淀该样本的链路）；
     *  期望行为组（三元组之二）显式引用上方 expected* 平铺字段，保持既有 itemsJson 兼容 */
    private String traceId;
}
