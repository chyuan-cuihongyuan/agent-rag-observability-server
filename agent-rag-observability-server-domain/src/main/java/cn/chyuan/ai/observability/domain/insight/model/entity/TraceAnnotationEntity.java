package cn.chyuan.ai.observability.domain.insight.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * trace 人工评分注解实体（工单 0150 U4，借鉴 Langfuse annotations）—
 * 主观质量资产化：1-5 分 + 判定依据，唯一键 (trace_id, operator)。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TraceAnnotationEntity {

    private Long id;
    /** 关联链路 traceId */
    private String traceId;
    /** 评分 1-5（1=很差，5=很好） */
    private Integer score;
    /** 判定依据备注 */
    private String note;
    /** 标注人（操作留痕） */
    private String operator;
    private String createTime;
    private String updateTime;
}
