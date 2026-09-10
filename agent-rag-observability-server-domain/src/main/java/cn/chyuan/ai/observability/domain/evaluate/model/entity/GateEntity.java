package cn.chyuan.ai.observability.domain.evaluate.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 评测门禁规则实体（工单 0136 R4）— 分层门禁配置：
 * <ul>
 *   <li>safetyDims：安全维度下限表 {dim: minSafety}（正向安全分口径，任一低于下限即一票否决；
 *       hallucination 配 0.8 等价幻觉率上限 0.2）</li>
 *   <li>scoreThresholds：质量分阈值表 {metric: min}（metric 可为 overall/passRate 或 Rubric 维度 key）</li>
 *   <li>trials：回测评测试验次数 k（Pass@k 执行链接入）</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GateEntity implements Serializable {
    private Long id;
    /** 门禁规则业务 ID（唯一） */
    private String gateId;
    /** 名称（全表唯一） */
    private String name;
    /** 安全维度 JSON 对象原文（存库形态，{"dim": minSafety}） */
    private String safetyDimsJson;
    /** 质量分阈值 JSON 对象原文（存库形态，{"metric": min}） */
    private String scoreThresholdsJson;
    /** 试验次数 k（回测创建任务时取该值） */
    private Integer trials;
    /** 是否启用 */
    private Boolean enabled;
    /** 解析后的安全维度下限表（应用层维护，落库时序列化回 safetyDimsJson） */
    private Map<String, Double> safetyDims;
    /** 解析后的质量分阈值表（应用层维护，落库时序列化回 scoreThresholdsJson） */
    private Map<String, Double> scoreThresholds;
    private String createTime;
    private String updateTime;
}
