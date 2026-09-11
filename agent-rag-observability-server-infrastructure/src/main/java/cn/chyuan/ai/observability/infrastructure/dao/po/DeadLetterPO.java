package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 死信记录表 PO（工单 0180 Y4）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeadLetterPO implements Serializable {
    private Long id;
    private String topicTag;
    private String payload;
    private Integer retryCount;
    private String lastError;
    private String status;
    private String createTime;
    private String updateTime;
}
