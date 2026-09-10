package cn.chyuan.ai.observability.domain.evaluate.model.entity;

import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricDimension;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 评测 Rubric 实体（工单 0133 R1）— 评判标准配置化：维度/权重/断言 prompt 模板/启停/版本。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RubricEntity implements Serializable {
    private Long id;
    /** Rubric 业务 ID（唯一） */
    private String rubricId;
    /** 名称（全表唯一） */
    private String name;
    /** 适配的评测类型（EvalType code）；内置种子按类型各挂一份 */
    private String evalType;
    /** 版本号 */
    private Integer version;
    /** 维度 JSON 数组原文（存库形态） */
    private String dimensionsJson;
    /** 解析后的维度列表（应用层维护，落库时序列化回 dimensionsJson） */
    private List<RubricDimension> dimensions;
    /** 是否启用 */
    private Boolean enabled;
    /** 内置种子标记（不可删改） */
    private Boolean builtin;
    private String createTime;
    private String updateTime;
}
