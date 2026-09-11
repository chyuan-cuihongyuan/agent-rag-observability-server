package cn.chyuan.ai.observability.api.dto.mining;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Case 候选 DTO（工单 0138 S2）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CaseCandidateDTO {
    private Long id;
    /** 来源：EVAL_LOW_SCORE / TRACE_FAIL / PATROL_FAIL */
    private String source;
    /** 来源内唯一引用 */
    private String sourceRef;
    /** 关联 traceId */
    private String traceId;
    /** 查询原文 */
    private String query;
    /** 答案摘要 */
    private String answerSummary;
    /** 命中文档数 */
    private Integer hitDocCount;
    /** 工具调用列表 JSON 数组原文 */
    private String toolList;
    /** 入池原因 */
    private String reason;
    /** 处置状态：PENDING / PROMOTED / IGNORED */
    private String status;
    /** 回填目标数据集 ID */
    private String promotedDatasetId;
    private String createTime;
}
