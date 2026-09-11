package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 配置变更事件表 PO（工单 0182 Y6）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigChangeEventPO implements Serializable {
    private Long id;
    private String tableName;
    private String bizKey;
    private String changesJson;
    private String operator;
    private String createTime;
}
