package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 评测 Rubric 表 PO（工单 0133 R1）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalRubricPO implements Serializable {
    private Long id;
    private String rubricId;
    private String name;
    private String evalType;
    private Integer version;
    private String dimensions;
    private Integer enabled;
    private Integer builtin;
    private String createTime;
    private String updateTime;
}
