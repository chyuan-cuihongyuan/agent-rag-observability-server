package cn.chyuan.ai.observability.domain.alert.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警静默窗口实体（工单 0179 Y3，借鉴 Alertmanager silence）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlertSilenceEntity {

    private Long id;
    /** 静默键（精确匹配告警键；前缀通配以 * 结尾） */
    private String silenceKey;
    private String startsAt;
    private String endsAt;
    /** 创建人（操作留痕） */
    private String createdBy;
    /** 原因（维护窗/已知问题） */
    private String reason;
    private String createTime;
}
