package cn.chyuan.ai.observability.domain.insight.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 漂移事件实体（工单 0154 U8）— 检索分数分布漂移留痕。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DriftEventEntity {

    private Long id;
    /** 指标名（当前固定 rerank_mean） */
    private String metric;
    /** 当前窗值 */
    private Double currentValue;
    /** 对比窗值 */
    private Double previousValue;
    /** 触发阈值 */
    private Double threshold;
    /** 上下文 JSON（样本数/空检索率/窗口） */
    private String detail;
    private String createTime;
}
