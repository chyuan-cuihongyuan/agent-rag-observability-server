package cn.chyuan.ai.observability.api.dto.evaluate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测门禁规则 DTO（工单 0136 R4）。
 * <ul>
 *   <li>safetyDimsJson：安全维度 JSON 对象原文 {"dim": minSafety}（正向安全分口径，
 *       任一低于下限即一票否决；hallucination 配 0.8 等价幻觉率上限 0.2）</li>
 *   <li>scoreThresholdsJson：质量分阈值 JSON 对象原文 {"metric": min}
 *       （metric 可为 overall/passRate 或 Rubric 维度 key）</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GateDTO {
    private String gateId;
    private String name;
    private String safetyDimsJson;
    private String scoreThresholdsJson;
    /** 试验次数 k（回测创建任务时取该值；1-20） */
    private Integer trials;
    private Boolean enabled;
    private String createTime;
    private String updateTime;
}
