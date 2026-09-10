package cn.chyuan.ai.observability.api.dto.evaluate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回测触发请求 DTO（工单 0136 R4）— datasetId（或 pool）+ gateId 创建回测任务。
 * <ul>
 *   <li>datasetId 与 pool 二选一：datasetId 直查指定数据集版本；pool 取该池最新一条</li>
 *   <li>evalType 缺省 ANSWER_QUALITY</li>
 *   <li>trials 取门禁配置（请求不覆盖，门禁是口径的唯一来源）</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BacktestRequestDTO {
    /** 数据集业务 ID（与 pool 二选一，优先） */
    private String datasetId;
    /** 样本池（golden/challenge/wrong；datasetId 为空时取该池最新一条） */
    private String pool;
    /** 门禁规则业务 ID（必填，须存在且启用） */
    private String gateId;
    /** 评测类型（缺省 ANSWER_QUALITY） */
    private String evalType;
    private String modelVersion;
    private String ragStrategyVersion;
}
