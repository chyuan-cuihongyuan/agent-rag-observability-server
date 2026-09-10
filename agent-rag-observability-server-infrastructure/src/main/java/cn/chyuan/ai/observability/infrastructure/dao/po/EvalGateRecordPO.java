package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 评测门禁记录表 PO（工单 0136 R4）。
 * triggerDetail 为触发明细 JSON 数组原文（[{ruleType,dim,actual,threshold,note}]）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalGateRecordPO implements Serializable {
    private Long id;
    private String recordId;
    private String gateId;
    private String taskId;
    private String result;
    private String triggerDetail;
    private String createTime;
}
