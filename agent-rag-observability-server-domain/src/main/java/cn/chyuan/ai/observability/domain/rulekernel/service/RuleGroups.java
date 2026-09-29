package cn.chyuan.ai.observability.domain.rulekernel.service;

import java.util.List;
import java.util.Map;

/**
 * 规则组解析（工单 1037 EM1，prometheus 规则组思想）。
 * name·interval·rules 解析/缺 name 拒绝/重复组拒绝（登记侧）。
 */
public final class RuleGroups {

    /** 规则：recording 或 alerting */
    public record Rule(String type, String name, String expr, long forTicks,
                       Map<String, String> labels, Map<String, String> annotations) {

        public Rule {
            if (!"recording".equals(type) && !"alerting".equals(type)) {
                throw new IllegalArgumentException("未知规则类型: " + type);
            }
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("规则缺 " + type + " 名");
            }
            if (expr == null || expr.isEmpty()) {
                throw new IllegalArgumentException("规则缺表达式: " + name);
            }
            if (forTicks < 0) {
                throw new IllegalArgumentException("for 为负: " + name);
            }
        }

        public boolean isAlerting() {
            return "alerting".equals(type);
        }
    }

    /** 规则组：名称 + 求值间隔 + 规则清单 */
    public record Group(String name, long intervalTicks, List<Rule> rules) {

        public Group {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("规则组缺 name");
            }
            if (intervalTicks <= 0) {
                throw new IllegalArgumentException("规则组 interval 须为正: " + name);
            }
            rules = List.copyOf(rules);
        }
    }

    private RuleGroups() {
    }
}
