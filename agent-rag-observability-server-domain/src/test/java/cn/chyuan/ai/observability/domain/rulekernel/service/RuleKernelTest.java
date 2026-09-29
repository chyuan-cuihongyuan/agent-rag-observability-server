package cn.chyuan.ai.observability.domain.rulekernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 告警规则内核测试（工单 1037-1044 EM1-EM8，prometheus 思想）。
 * 规则组解析/recording 写序列/alert 状态机/for 持续/模板渲染/resolve 恢复/指纹去重/端口组合管线。
 */
class RuleKernelTest {

    @Test
    void ruleGroupParse() {
        RuleGroups.Group group = new RuleGroups.Group("app", 1, List.of(
                new RuleGroups.Rule("recording", "job:up:sum", "up", 0, Map.of("job", "rulekernel"), Map.of()),
                new RuleGroups.Rule("alerting", "InstanceDown", "up == 0", 3, Map.of("severity", "page"), Map.of())));
        assertEquals("app", group.name());
        assertEquals(2, group.rules().size());
        assertTrue(group.rules().get(1).isAlerting());

        assertThrows(IllegalArgumentException.class,
                () -> new RuleGroups.Group("", 1, List.of()), "缺 name 拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new RuleGroups.Group("app", 0, List.of()), "零 interval 拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new RuleGroups.Rule("recording", "", "up", 0, Map.of(), Map.of()), "缺 record 名拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new RuleGroups.Rule("alerting", "A", "", 0, Map.of(), Map.of()), "缺表达式拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new RuleGroups.Rule("query", "A", "up", 0, Map.of(), Map.of()), "未知类型拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new RuleGroups.Rule("alerting", "A", "up", -1, Map.of(), Map.of()), "负 for 拒绝");
    }

    @Test
    void recordingRuleWrite() {
        RecordingRules recordings = new RecordingRules();
        recordings.write("job:up:sum", Map.of("dc", "cn1"), 3, 1);
        recordings.write("job:up:sum", Map.of("dc", "cn1"), 2, 2);
        recordings.write("job:down:sum", Map.of(), 0, 2);
        assertEquals(3, recordings.size());
        assertEquals(2, recordings.seriesOf("job:up:sum").size());
        assertEquals("rulekernel", recordings.series().get(0).labels().get("job"), "label 注入");
        assertEquals(2, recordings.series().get(1).value());
        assertThrows(IllegalArgumentException.class,
                () -> recordings.write("", Map.of(), 1, 1), "缺 record 名拒绝");
        assertThrows(IllegalArgumentException.class, () -> recordings.write(null, Map.of(), 1, 1), "空拒绝");
    }

    @Test
    void alertStateTransitions() {
        assertEquals(AlertStates.State.PENDING,
                AlertStates.transition(AlertStates.State.INACTIVE, AlertStates.State.PENDING), "inactive→pending");
        assertEquals(AlertStates.State.FIRING,
                AlertStates.transition(AlertStates.State.PENDING, AlertStates.State.FIRING), "pending→firing");
        assertEquals(AlertStates.State.INACTIVE,
                AlertStates.transition(AlertStates.State.PENDING, AlertStates.State.INACTIVE), "pending→inactive");
        assertEquals(AlertStates.State.INACTIVE,
                AlertStates.transition(AlertStates.State.FIRING, AlertStates.State.INACTIVE), "firing→inactive");
        assertEquals(AlertStates.State.FIRING,
                AlertStates.transition(AlertStates.State.FIRING, AlertStates.State.FIRING), "自迁移幂等");
        assertThrows(IllegalStateException.class,
                () -> AlertStates.transition(AlertStates.State.INACTIVE, AlertStates.State.FIRING), "非法跃迁拒绝");
        assertThrows(IllegalStateException.class,
                () -> AlertStates.transition(AlertStates.State.FIRING, AlertStates.State.PENDING), "非法跃迁拒绝");
    }

    @Test
    void forDurationDue() {
        assertTrue(ForDuration.due(0, 3, 3), "pending 满 for 触发");
        assertTrue(ForDuration.due(0, 5, 3), "超过 for 触发");
        assertTrue(ForDuration.due(2, 2, 0), "for=0 即到即触发");
        assertFalse(ForDuration.due(0, 2, 3), "未满不触发");
        assertEquals(-1, ForDuration.reset(), "ActiveAt 清除");
        assertEquals(7, ForDuration.restart(7), "重新计时取当前 tick");
        assertThrows(IllegalArgumentException.class, () -> ForDuration.due(-1, 1, 1), "负 activeAt 拒绝");
    }

    @Test
    void templateRender() {
        Map<String, String> labels = Map.of("instance", "host-1", "severity", "page");
        assertEquals("host-1 down", Templates.render("{{$labels.instance}} down", labels, 1));
        assertEquals("value=3", Templates.render("value={{$value}}", labels, 3));
        assertEquals("value=0.5", Templates.render("value={{$value}}", labels, 0.5));
        assertEquals("missing:", Templates.render("missing:{{$labels.nolabel}}", labels, 1), "缺失标签空串");
        assertEquals("host-1 page alert", Templates.render("{{$labels.instance}} {{$labels.severity}} alert", labels, 1),
                "注解渲染多占位");
        assertEquals("keep {{unknown}}", Templates.render("keep {{unknown}}", labels, 1), "未知占位原样");
        assertEquals("literal", Templates.render("literal", labels, 1), "无占位原样");
        assertThrows(IllegalArgumentException.class, () -> Templates.render(null, labels, 1), "空模板拒绝");
    }

    @Test
    void resolveClearsAndRestarts() {
        Resolves resolves = new Resolves();
        String fp = Fingerprints.of("InstanceDown", Map.of("instance", "host-1"));
        Resolves.Active active = resolves.trigger(fp, "InstanceDown", Map.of("instance", "host-1"), 1);
        assertEquals(1, active.activeAt());
        assertEquals(AlertStates.State.PENDING, active.state());
        resolves.trigger(fp, "InstanceDown", Map.of("instance", "host-1"), 5);
        assertEquals(1, resolves.get(fp).activeAt(), "重复触发保持原计时");

        assertTrue(resolves.resolve(fp), "条件消失 resolve");
        assertFalse(resolves.isActive(fp), "ActiveAt 清除并移除");
        assertFalse(resolves.resolve(fp), "重复 resolve 返回 false");

        Resolves.Active restarted = resolves.trigger(fp, "InstanceDown", Map.of("instance", "host-1"), 9);
        assertEquals(9, restarted.activeAt(), "再触发重新计时");
        assertTrue(resolves.resolve(Fingerprints.of("Other", Map.of())) == false, "未知指纹 resolve false");
    }

    @Test
    void fingerprintDedup() {
        String fp1 = Fingerprints.of("InstanceDown", Map.of("instance", "host-1"));
        String fp2 = Fingerprints.of("InstanceDown", Map.of("instance", "host-1"));
        String fp3 = Fingerprints.of("InstanceDown", Map.of("instance", "host-2"));
        String fp4 = Fingerprints.of("HostDown", Map.of("instance", "host-1"));
        assertEquals(fp1, fp2, "同规则同标签指纹一致");
        assertNotEquals(fp1, fp3, "标签变化生成新指纹");
        assertNotEquals(fp1, fp4, "规则名变化生成新指纹");
        assertTrue(Fingerprints.distinct("r", Map.of("a", "1"), Map.of("a", "2")));
        assertFalse(Fingerprints.distinct("r", Map.of("a", "1"), Map.of("a", "1")));
        assertThrows(IllegalArgumentException.class, () -> Fingerprints.of("", Map.of()), "空规则名拒绝");
    }

    @Test
    void rulePortPipeline() {
        RulePort port = RulePort.inMemory();
        port.loadGroup(new RuleGroups.Group("app", 1, List.of(
                new RuleGroups.Rule("recording", "job:up:sum", "sum(up)", 0, Map.of("dc", "cn1"), Map.of()),
                new RuleGroups.Rule("alerting", "InstanceDown", "up", 2,
                        Map.of("instance", "host-1"), Map.of("summary", "{{$labels.instance}} down value {{$value}}")))));
        assertThrows(IllegalStateException.class,
                () -> port.loadGroup(new RuleGroups.Group("app", 1, List.of())), "重复组拒绝");

        port.setMetric("up", 1.0);
        port.setMetric("sum(up)", 2.0);
        List<RecordingRules.Sample> fired0 = port.eval();
        assertTrue(fired0.isEmpty(), "首轮 pending 未满 for");
        assertEquals(1, port.seriesOf("job:up:sum").size(), "recording 写序列");
        assertEquals(1, port.activeAlerts().size());
        assertEquals(AlertStates.State.PENDING, port.activeAlerts().get(0).state());

        port.eval();
        assertTrue(port.activeAlerts().get(0).state() == AlertStates.State.PENDING, "for 未满继续 pending");
        List<RecordingRules.Sample> fired = port.eval();
        assertEquals(1, fired.size(), "for 满触发");
        assertEquals(AlertStates.State.FIRING, port.activeAlerts().get(0).state());
        assertEquals("host-1 down value 1", port.activeAlerts().get(0).annotation(), "模板渲染");

        // 同指纹不重复触发
        List<RecordingRules.Sample> refired = port.eval();
        assertTrue(refired.isEmpty(), "同 fingerprint 不重复触发");

        // 条件消失恢复
        port.setMetric("up", 0.0);
        port.eval();
        assertTrue(port.activeAlerts().isEmpty(), "条件消失 firing→inactive");

        // 再触发重新计时
        port.setMetric("up", 1.0);
        port.eval();
        assertEquals(AlertStates.State.PENDING, port.activeAlerts().get(0).state(), "再触发回 pending 重新计时");

        // tskernel 样本形状只读联动
        String shape = port.sampleShape(port.seriesOf("job:up:sum").get(0));
        assertTrue(shape.startsWith("job:up:sum{"), "样本形状串");
        assertTrue(shape.contains("dc=cn1") && shape.contains("@1"), "形状含标签与 tick: " + shape);
    }
}
