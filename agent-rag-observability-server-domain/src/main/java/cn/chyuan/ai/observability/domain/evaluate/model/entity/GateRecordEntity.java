package cn.chyuan.ai.observability.domain.evaluate.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 评测门禁记录实体（工单 0136 R4）— 回测执行完门禁判定的结论落账：
 * 结论 PASS/BLOCK + 触发规则明细 JSON + 时间，供 CI/CD 与前端查询。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GateRecordEntity implements Serializable {
    private Long id;
    /** 门禁记录业务 ID（唯一） */
    private String recordId;
    /** 门禁规则业务 ID */
    private String gateId;
    /** 回测评测任务业务 ID */
    private String taskId;
    /** 门禁结论：PASS-放行，BLOCK-拦截（安全越限/分数越限/任务失败） */
    private String result;
    /** 触发明细 JSON 数组原文 [{ruleType,dim,actual,threshold,note}]（PO String） */
    private String triggerDetail;
    private String createTime;
}
