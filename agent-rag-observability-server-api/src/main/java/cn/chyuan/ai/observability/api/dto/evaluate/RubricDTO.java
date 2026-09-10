package cn.chyuan.ai.observability.api.dto.evaluate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rubric 评判标准 DTO（工单 0133 R1）。
 * dimensionsJson 为维度 JSON 数组原文：[{key,label,weight,judgePrompt,binary}]。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RubricDTO {
    private String rubricId;
    private String name;
    private String evalType;
    private Integer version;
    private String dimensionsJson;
    private Boolean enabled;
    private Boolean builtin;
    private String createTime;
    private String updateTime;
}
