package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 告警静默表 PO（工单 0179 Y3）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlertSilencePO implements Serializable {
    private Long id;
    private String silenceKey;
    private String startsAt;
    private String endsAt;
    private String createdBy;
    private String reason;
    private String createTime;
}
