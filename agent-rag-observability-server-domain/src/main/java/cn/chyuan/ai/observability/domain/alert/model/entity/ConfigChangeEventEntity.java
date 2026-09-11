package cn.chyuan.ai.observability.domain.alert.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置变更事件实体（工单 0182 Y6）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigChangeEventEntity {

    private Long id;
    /** 配置表名（如 eval_gate / alert_silence / feature_flag） */
    private String tableName;
    /** 业务键（如 gateId） */
    private String bizKey;
    /** 变更明细 JSON 数组 [{field,from,to}]（敏感值脱敏） */
    private String changesJson;
    /** 操作者（留痕） */
    private String operator;
    private String createTime;
}
