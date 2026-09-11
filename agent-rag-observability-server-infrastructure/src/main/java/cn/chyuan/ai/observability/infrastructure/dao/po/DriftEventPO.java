package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 漂移事件表 PO（工单 0154 U8）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DriftEventPO implements Serializable {
    private Long id;
    private String metric;
    private Double currentValue;
    private Double previousValue;
    private Double threshold;
    private String detail;
    private String createTime;
}
