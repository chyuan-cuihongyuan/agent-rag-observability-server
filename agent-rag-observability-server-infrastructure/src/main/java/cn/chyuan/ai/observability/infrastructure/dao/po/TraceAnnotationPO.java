package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 人工注解表 PO（工单 0150 U4）— 唯一键 uk_annotation (trace_id, operator)。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TraceAnnotationPO implements Serializable {
    private Long id;
    private String traceId;
    private Integer score;
    private String note;
    private String operator;
    private String createTime;
    private String updateTime;
}
