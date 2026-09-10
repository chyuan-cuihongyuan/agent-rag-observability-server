package cn.chyuan.ai.observability.api.dto.evaluate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测门禁记录 DTO（工单 0136 R4）— 回测判定结论（PASS/BLOCK）+ 触发规则明细。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GateRecordDTO {
    private String recordId;
    /** 门禁规则业务 ID */
    private String gateId;
    /** 回测评测任务业务 ID */
    private String taskId;
    /** 门禁结论：PASS-放行，BLOCK-拦截 */
    private String result;
    /** 触发明细 JSON 数组原文 [{ruleType,dim,actual,threshold,note}] */
    private String triggerDetail;
    private String createTime;
}
