package cn.chyuan.ai.observability.domain.evaluate.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass@k 统计纯函数测试（工单 0135 R3 验收）— 构造确定性 per-trial 分数验证公式：
 * <ul>
 *   <li>样本通过 = k 次 trial 至少 1 次综合分达标（HumanEval 口径）</li>
 *   <li>passRate = pass 样本数/样本总数（分母是样本数而非结果行数）</li>
 *   <li>方差 = k 个 trial 均值的总体标准差（k=1 恒为 0）</li>
 *   <li>k=1 退化为传统单次通过率（回归口径）</li>
 * </ul>
 */
@DisplayName("Pass@k 通过率与方差计算测试")
class PassAtKCalculatorTest {

    // ===== compute：passRate 与 trialMeans =====

    @Test
    @DisplayName("Pass@k 标准口径 — 样本在 k 次中至少 1 次达标即通过")
    public void testPassAtK_AtLeastOncePass() {
        // 3 个样本 × 3 次 trial：
        //   s1: [0.2, 0.8, 0.3] → trial2 达标(>=0.5) → pass
        //   s2: [0.9, 0.7, 0.85] → 全达标 → pass
        //   s3: [0.1, 0.4, 0.49] → 从未达标 → fail
        PassAtKCalculator.PassAtKResult r = PassAtKCalculator.compute(3, null, List.of(
                List.of(0.2, 0.8, 0.3),
                List.of(0.9, 0.7, 0.85),
                List.of(0.1, 0.4, 0.49)));
        assertEquals(2.0 / 3.0, r.passRate(), 1e-9, "2/3 样本至少一次达标");
        // trialMeans：trial1=(0.2+0.9+0.1)/3, trial2=(0.8+0.7+0.4)/3, trial3=(0.3+0.85+0.49)/3
        assertEquals(1.2 / 3.0, r.trialMeans().get(0), 1e-9);
        assertEquals(1.9 / 3.0, r.trialMeans().get(1), 1e-9);
        assertEquals(1.64 / 3.0, r.trialMeans().get(2), 1e-9);
    }

    @Test
    @DisplayName("k=1 回归口径 — 退化为传统单次通过率（阈值以上计 pass）")
    public void testPassAtK_K1Degenerates() {
        PassAtKCalculator.PassAtKResult r = PassAtKCalculator.compute(1, null, List.of(
                List.of(0.9), List.of(0.49), List.of(0.5)));
        assertEquals(2.0 / 3.0, r.passRate(), 1e-9, "k=1：0.9 与 0.5（等于阈值）达标");
        assertEquals(1, r.trialMeans().size());
    }

    @Test
    @DisplayName("阈值可配 — 自定义 passThreshold 改变通过判定")
    public void testPassAtK_CustomThreshold() {
        // 默认 0.5 下 [0.3, 0.55] 达标；阈值 0.6 下不达标
        assertEquals(1.0, PassAtKCalculator.compute(2, null,
                List.of(List.of(0.3, 0.55))).passRate(), 1e-9);
        assertEquals(0.0, PassAtKCalculator.compute(2, 0.6,
                List.of(List.of(0.3, 0.55))).passRate(), 1e-9);
    }

    @Test
    @DisplayName("默认阈值 = 0.5（null 视为默认，沿用 Rubric 引擎达标线）")
    public void testDefaultThreshold() {
        PassAtKCalculator.PassAtKResult r = PassAtKCalculator.compute(1, null,
                List.of(List.of(0.5)));
        assertEquals(1.0, r.passRate(), 1e-9, "等于 0.5 视为达标");
        assertEquals(0.5, PassAtKCalculator.DEFAULT_PASS_THRESHOLD, 0.0);
    }

    @Test
    @DisplayName("边界口径 — 综合分等于阈值视为达标（>=）")
    public void testBoundaryEqualsThresholdPasses() {
        PassAtKCalculator.PassAtKResult r = PassAtKCalculator.compute(2, 0.7, List.of(
                List.of(0.7, 0.1),
                List.of(0.6999, 0.699)));
        assertEquals(0.5, r.passRate(), 1e-9, "仅第一个样本恰有一次等于阈值");
    }

    @Test
    @DisplayName("空与非法输入 — 空样本表 passRate=0；trials<1 结构化拒绝")
    public void testEmptyAndIllegal() {
        assertEquals(0.0, PassAtKCalculator.compute(1, null, List.of()).passRate(), 0.0);
        assertEquals(0.0, PassAtKCalculator.compute(3, null, null).passRate(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> PassAtKCalculator.compute(0, null, List.of()));
        // 样本行 null / 分数 null 容错不计入
        PassAtKCalculator.PassAtKResult r = PassAtKCalculator.compute(1, null,
                java.util.Arrays.asList(null, null));
        assertEquals(0.0, r.passRate(), 0.0);
    }

    // ===== stdDevOfTrialMeans：方差（标准差） =====

    @Test
    @DisplayName("方差公式 — k 个 trial 均值的总体标准差（手算对照）")
    public void testStdDevFormula() {
        // trial 均值 [0.6, 0.8]：均值 0.7，总体方差 = (0.01+0.01)/2 = 0.01，标准差 0.1
        assertEquals(0.1, PassAtKCalculator.stdDevOfTrialMeans(List.of(0.6, 0.8)), 1e-9);
        // [0.2, 0.5, 0.8]：均值 0.5，方差 = (0.09+0+0.09)/3 = 0.06
        assertEquals(Math.sqrt(0.06), PassAtKCalculator.stdDevOfTrialMeans(List.of(0.2, 0.5, 0.8)), 1e-9);
    }

    @Test
    @DisplayName("k=1 与空列表方差恒为 0（k=1 无波动，行为良好）")
    public void testStdDevK1AlwaysZero() {
        assertEquals(0.0, PassAtKCalculator.stdDevOfTrialMeans(List.of(0.73)), 0.0);
        assertEquals(0.0, PassAtKCalculator.stdDevOfTrialMeans(List.of()), 0.0);
        assertEquals(0.0, PassAtKCalculator.stdDevOfTrialMeans(null), 0.0);
    }

    @Test
    @DisplayName("k 次 trial 均值全相同 → 方差 0（离线复用确定性场景）")
    public void testStdDevIdenticalTrials() {
        assertEquals(0.0, PassAtKCalculator.stdDevOfTrialMeans(List.of(0.66, 0.66, 0.66)), 0.0);
    }

    // ===== avgOfAll / dimensionAverage =====

    @Test
    @DisplayName("综合分均值 — 全部 trial 全部样本算术平均（与 avg_overall_score 同口径）")
    public void testAvgOfAll() {
        double avg = PassAtKCalculator.avgOfAll(List.of(
                List.of(0.2, 0.4),
                List.of(0.6, 0.8)));
        assertEquals(0.5, avg, 1e-9);
        assertEquals(0.0, PassAtKCalculator.avgOfAll(null), 0.0);
        assertEquals(0.0, PassAtKCalculator.avgOfAll(List.of()), 0.0);
    }

    @Test
    @DisplayName("维度均值聚合 — 按结果行数平均（门禁安全维度数据源）")
    public void testDimensionAverage() {
        Map<String, Double> avg = PassAtKCalculator.dimensionAverage(
                Map.of("hallucination", 2.4, "faithfulness", 1.2), 4);
        assertEquals(0.6, avg.get("hallucination"), 1e-9, "正向安全分：2.4/4");
        assertEquals(0.3, avg.get("faithfulness"), 1e-9);
        assertTrue(PassAtKCalculator.dimensionAverage(Map.of("f1", 1.0), 0).isEmpty(), "行数 0 返回空表");
    }

    // ===== 端到端小场景：方差与通过率联合验证 =====

    @Test
    @DisplayName("联合场景 — 在线回放随机（trial 间波动）方差>0，离线确定性方差=0")
    public void testOnlineVsOffline() {
        // 在线回放：同一批样本两次 trial 分数不同（LLM/检索随机性）
        PassAtKCalculator.PassAtKResult online = PassAtKCalculator.compute(2, 0.5, List.of(
                List.of(0.3, 0.6),
                List.of(0.4, 0.2)));
        assertTrue(PassAtKCalculator.stdDevOfTrialMeans(online.trialMeans()) > 0, "在线回放 trial 间应有波动");
        assertEquals(0.5, online.passRate(), 1e-9);

        // 离线复用：两次 trial 分数完全一致（同一 trace 确定性）
        PassAtKCalculator.PassAtKResult offline = PassAtKCalculator.compute(2, 0.5, List.of(
                List.of(0.3, 0.3),
                List.of(0.4, 0.4)));
        assertEquals(0.0, PassAtKCalculator.stdDevOfTrialMeans(offline.trialMeans()), 0.0, "离线复用方差恒 0");
        assertEquals(0.0, offline.passRate(), 1e-9, "确定性下 Pass@2 与 Pass@1 同口径（两样本单次均不达标）");
    }
}
