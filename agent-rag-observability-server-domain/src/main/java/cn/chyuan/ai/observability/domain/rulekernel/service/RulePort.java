package cn.chyuan.ai.observability.domain.rulekernel.service;

import java.util.List;
import java.util.Map;

import cn.chyuan.ai.observability.domain.rulekernel.service.RuleGroups.Group;

/**
 * 告警规则端口（工单 1044 EM8，prometheus 思想）。
 * load·eval 入口统一编排/与 tskernel 样本序列作样本形状只读联动（泛型形状串不 import）/
 * rule-kernel.enabled 默认关（开启才改变行为）。
 */
public interface RulePort {

    /** 装载规则组：重复组名拒绝 */
    void loadGroup(Group group);

    /** 数据面：设置表达式对应取值（空移除） */
    void setMetric(String expr, Double value);

    /** 求值一轮：recording 写序列、alerting 状态机推进；返回本轮新触发告警 */
    List<RecordingRules.Sample> eval();

    /** recording 样本序列 */
    List<RecordingRules.Sample> series();

    /** 指定 record 的序列 */
    List<RecordingRules.Sample> seriesOf(String record);

    /** 活跃告警（PENDING/FIRING） */
    List<Resolves.Active> activeAlerts();

    /** 当前 tick */
    long now();

    /** tskernel 样本序列形态只读联动：样本形状串 metric{a=b,...} value @tick（形状数据不 import tskernel） */
    String sampleShape(RecordingRules.Sample sample);

    static RulePort inMemory() {
        return new RuleEngine();
    }
}
