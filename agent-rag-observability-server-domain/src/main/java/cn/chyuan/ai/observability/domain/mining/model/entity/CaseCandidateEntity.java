package cn.chyuan.ai.observability.domain.mining.model.entity;

import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseSource;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Case 候选实体（工单 0138 S2）— 挖掘枢纽产出的统一候选形态，
 * 回填/忽略后仍保留归因态（0139 S3 在此基础上扩展归因字段）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CaseCandidateEntity {

    private Long id;
    /** 来源（三来源枚举） */
    private CaseSource source;
    /** 来源内唯一引用：评测=taskId:trialNo:traceId、链路=traceId、巡检=pid-记录ID（幂等键一半） */
    private String sourceRef;
    /** 关联 traceId（评测在线回放与链路来源可直接关查详情） */
    private String traceId;
    /** 查询原文（回填错题集时作为 prompt） */
    private String query;
    /** 答案摘要（截断存储，快照上下文） */
    private String answerSummary;
    /** 命中文档数（检索上下文快照） */
    private Integer hitDocCount;
    /** 工具调用列表 JSON 数组原文（工具上下文快照，可空） */
    private String toolList;
    /** 入池原因（分数值/失败状态/巡检错误摘要） */
    private String reason;
    /** 处置状态：PENDING/PROMOTED/IGNORED */
    private CaseStatus status;
    /** 回填目标数据集（PROMOTED 时有值） */
    private String promotedDatasetId;
    private String createTime;
}
