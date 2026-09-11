package cn.chyuan.ai.observability.domain.cost.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型计价实体（工单 0148 U2）— 输入/输出千 token 单价（币种单位由运营口径定，本文不换算）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelPricingEntity {

    private Long id;
    /** 模型名（与 chat_result_log.model_version 匹配，唯一） */
    private String model;
    /** 输入单价：每 1K prompt token */
    private Double inputPricePer1k;
    /** 输出单价：每 1K completion token */
    private Double outputPricePer1k;
    /** 备注（币种/生效口径） */
    private String remark;
    private String updateTime;
}
