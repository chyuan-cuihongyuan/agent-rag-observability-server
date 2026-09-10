package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 评测门禁规则表 PO（工单 0136 R4）。
 * safetyDims/scoreThresholds 为 JSON 对象原文（{dim: threshold}），应用层校验与解析。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalGatePO implements Serializable {
    private Long id;
    private String gateId;
    private String name;
    private String safetyDims;
    private String scoreThresholds;
    private Integer trials;
    private Integer enabled;
    private String createTime;
    private String updateTime;
}
