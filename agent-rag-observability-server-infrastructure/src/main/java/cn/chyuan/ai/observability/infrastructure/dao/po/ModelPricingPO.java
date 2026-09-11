package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 模型计价表 PO（工单 0148 U2）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelPricingPO implements Serializable {
    private Long id;
    private String model;
    private Double inputPricePer1k;
    private Double outputPricePer1k;
    private String remark;
    private String updateTime;
}
