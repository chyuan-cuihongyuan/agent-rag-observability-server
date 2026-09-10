package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.TaskEvalSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 门禁判定引擎纯函数测试（工单 0136 R4 验收）：
 * 一票否决优先级三场景 + 阈值边界（等于阈值=达标）+ 维度缺失/任务失败兜底。
 */
@DisplayName("门禁判定引擎测试")
class GateDecisionEngineTest {

    private final GateDecisionEngine engine = new GateDecisionEngine();

    /** 通用门禁：safety {hallucination>=0.8, faithfulness>=0.7} + score {overall>=0.6, passRate>=0.5} */
    private GateEntity gate(Map<String, Double> safety, Map<String, Double> score) {
        return GateEntity.builder()
                .gateId("gate-1").name("发布门禁").trials(3).enabled(true)
                .safetyDims(safety).scoreThresholds(score)
                .build();
    }

    /** 通用汇总：全维度 0.9、综合分 0.85、通过率 0.9（全达标基线） */
    private TaskEvalSummary summary(Double overall, Double passRate, Map<String, Double> dims) {
        return TaskEvalSummary.builder()
                .taskId("task-1").trials(3).sampleCount(10)
                .avgOverall(overall).passRate(passRate).scoreStdDev(0.01)
                .dimensionAvg(dims)
                .build();
    }

    private Map<String, Double> dims(Object... kv) {
        java.util.Map<String, Double> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Double) kv[i + 1]);
        }
        return m;
    }

    // ===== 一票否决优先级三场景 =====

    @Test
    @DisplayName("场景一：安全越限 + 总分达标 → BLOCK（一票否决优先于总分）")
    public void testSafetyVeto_BlocksDespiteScorePass() {
        // hallucination 安全分 0.7 < 0.8（等价幻觉率 0.3 > 0.2 上限），overall/passRate 均达标
        TaskEvalSummary s = summary(0.9, 0.9, dims("hallucination", 0.7, "faithfulness", 0.9));
        GateDecisionEngine.GateDecision d = engine.judge(s, gate(
                Map.of("hallucination", 0.8, "faithfulness", 0.7),
                Map.of("overall", 0.6, "passRate", 0.5)));

        assertFalse(d.isPass(), "安全维度越限必须 BLOCK，即使总分达标");
        assertEquals(GateDecisionEngine.RESULT_BLOCK, d.result());
        assertEquals(1, d.triggers().size(), "仅安全层触发，分数层全部达标无明细");
        GateDecisionEngine.GateTrigger t = d.triggers().get(0);
        assertEquals("SAFETY", t.ruleType());
        assertEquals("hallucination", t.dim());
        assertEquals(0.7, t.actual(), 1e-9);
        assertEquals(0.8, t.threshold(), 1e-9);
    }

    @Test
    @DisplayName("场景二：安全达标 + 总分越限 → BLOCK（附触发明细）")
    public void testScoreViolation_BlocksWithDetail() {
        // overall 0.5 < 0.6、passRate 0.4 < 0.5，安全全达标
        TaskEvalSummary s = summary(0.5, 0.4, dims("hallucination", 0.95, "faithfulness", 0.9));
        GateDecisionEngine.GateDecision d = engine.judge(s, gate(
                Map.of("hallucination", 0.8, "faithfulness", 0.7),
                Map.of("overall", 0.6, "passRate", 0.5)));

        assertFalse(d.isPass());
        assertEquals(2, d.triggers().size(), "两个分数指标越限均应出明细");
        assertTrue(d.triggers().stream().allMatch(t -> "SCORE".equals(t.ruleType())));
        assertTrue(d.triggers().stream().anyMatch(t -> "overall".equals(t.dim())));
        assertTrue(d.triggers().stream().anyMatch(t -> "passRate".equals(t.dim())));
    }

    @Test
    @DisplayName("场景三：安全与分数全达标 → PASS（无触发明细）")
    public void testAllPass() {
        TaskEvalSummary s = summary(0.85, 0.9, dims("hallucination", 0.9, "faithfulness", 0.8));
        GateDecisionEngine.GateDecision d = engine.judge(s, gate(
                Map.of("hallucination", 0.8, "faithfulness", 0.7),
                Map.of("overall", 0.6, "passRate", 0.5)));

        assertTrue(d.isPass());
        assertEquals(GateDecisionEngine.RESULT_PASS, d.result());
        assertTrue(d.triggers().isEmpty());
    }

    @Test
    @DisplayName("双层同时越限 — 明细全部收集（安全+分数），结论 BLOCK")
    public void testBothLayersViolated() {
        TaskEvalSummary s = summary(0.3, 0.2, dims("hallucination", 0.5, "faithfulness", 0.9));
        GateDecisionEngine.GateDecision d = engine.judge(s, gate(
                Map.of("hallucination", 0.8),
                Map.of("overall", 0.6)));

        assertFalse(d.isPass());
        assertEquals(2, d.triggers().size());
        assertTrue(d.triggers().stream().anyMatch(t -> "SAFETY".equals(t.ruleType())));
        assertTrue(d.triggers().stream().anyMatch(t -> "SCORE".equals(t.ruleType())));
    }

    // ===== 阈值边界口径：等于阈值=达标（两侧统一闭区间下限） =====

    @Test
    @DisplayName("边界口径 — 安全分恰等于下限视为达标（不触发一票否决）")
    public void testBoundarySafetyEqualsLimitPasses() {
        TaskEvalSummary s = summary(0.9, 0.9, dims("hallucination", 0.8));
        GateDecisionEngine.GateDecision d = engine.judge(s, gate(
                Map.of("hallucination", 0.8), Map.of("overall", 0.6)));
        assertTrue(d.isPass(), "safety value >= limit 达标：0.8 >= 0.8 不越限");

        // 略低于下限即越限（严格小于）
        TaskEvalSummary s2 = summary(0.9, 0.9, dims("hallucination", 0.7999));
        assertFalse(engine.judge(s2, gate(Map.of("hallucination", 0.8), Map.of())).isPass(),
                "0.7999 < 0.8 严格小于即越限");
    }

    @Test
    @DisplayName("边界口径 — 分数恰等于阈值视为达标（维度 key / overall / passRate 三处）")
    public void testBoundaryScoreEqualsMinPasses() {
        // 维度 key：faithfulness 0.7 >= 0.7
        assertTrue(engine.judge(summary(0.9, 0.9, dims("faithfulness", 0.7)),
                gate(Map.of(), Map.of("faithfulness", 0.7))).isPass());
        // overall 汇总 key：0.6 >= 0.6
        assertTrue(engine.judge(summary(0.6, 0.9, dims()),
                gate(Map.of(), Map.of("overall", 0.6))).isPass());
        // passRate 汇总 key：0.5 >= 0.5
        assertTrue(engine.judge(summary(0.9, 0.5, dims()),
                gate(Map.of(), Map.of("passRate", 0.5))).isPass());
    }

    // ===== 维度缺失与任务失败兜底 =====

    @Test
    @DisplayName("安全维度缺失 — 按 0 计并注明（安全口径从严），触发一票否决")
    public void testMissingSafetyDimCountsZero() {
        // 汇总缺 hallucination 维度（纯检索评测无 LLM 评判）
        TaskEvalSummary s = summary(0.9, 0.9, dims("f1", 0.8));
        GateDecisionEngine.GateDecision d = engine.judge(s, gate(
                Map.of("hallucination", 0.8), Map.of()));

        assertFalse(d.isPass(), "缺失维度按 0 计 → 0 < 0.8 越限 BLOCK");
        assertEquals("SAFETY", d.triggers().get(0).ruleType());
        assertTrue(d.triggers().get(0).note().contains("维度缺失"));
    }

    @Test
    @DisplayName("分数指标缺失 — 按 0 计（指标缺失按 0 计），同样 BLOCK")
    public void testMissingScoreMetricCountsZero() {
        TaskEvalSummary s = summary(0.9, 0.9, dims("f1", 0.8));
        GateDecisionEngine.GateDecision d = engine.judge(s, gate(
                Map.of(), Map.of("overall", 0.6, "mrr", 0.5)));

        assertFalse(d.isPass(), "overall 走汇总达标，但 mrr 维度缺失按 0 → 0 < 0.5 越限");
        assertTrue(d.triggers().stream().anyMatch(t -> "mrr".equals(t.dim())));
    }

    @Test
    @DisplayName("任务失败/无汇总 — summary=null 按 BLOCK 处理（防回测挂掉静默放行）")
    public void testTaskFailedBlocks() {
        GateDecisionEngine.GateDecision d = engine.judge(null, gate(Map.of(), Map.of()));
        assertFalse(d.isPass());
        assertEquals("TASK_FAILED", d.triggers().get(0).ruleType());
        assertNotNull(d.triggers().get(0).note());
    }

    @Test
    @DisplayName("gate 为空 — 结构化拒绝；空规则门禁 — 恒 PASS（无规则即无约束）")
    public void testIllegalGate() {
        assertThrows(IllegalArgumentException.class, () -> engine.judge(
                summary(0.1, 0.1, dims()), null));
        assertTrue(engine.judge(summary(0.1, 0.1, dims()), gate(null, null)).isPass(),
                "两层规则均未配置时无触发条件");
    }

    @Test
    @DisplayName("null 容错 — safetyDims/scoreThresholds/dimensionAvg 为 null 不抛异常")
    public void testNullTolerant() {
        TaskEvalSummary s = summary(0.9, 0.9, null);
        assertTrue(engine.judge(s, gate(null, Map.of("overall", 0.6))).isPass());
    }
}
