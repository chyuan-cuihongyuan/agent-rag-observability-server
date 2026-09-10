package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmJudgePort;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.RubricEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.DimensionVerdict;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricDimension;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricOutcome;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rubric 执行引擎单元测试（工单 0133 R1 验收）：
 * 二元断言解析——合法 0|1、非法输出→unknown、缺证据→unknown；
 * 加权综合分（unknown 剔除后归一化）+ unknown 占比；连续分维度与确定性维度注入。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Rubric 执行引擎测试")
public class RubricExecutionEngineTest {

    @Mock
    private ILlmJudgePort llmJudgePort;

    @InjectMocks
    private RubricExecutionEngine engine;

    private static RubricDimension binaryDim(String key, double weight, String prompt) {
        return RubricDimension.builder().key(key).label(key).weight(weight)
                .judgePrompt(prompt).binary(true).build();
    }

    private static RubricEntity rubricOf(RubricDimension... dims) {
        return RubricEntity.builder().rubricId("r-test").name("测试").dimensions(List.of(dims)).build();
    }

    @Test
    @DisplayName("二元断言 — 合法输出 verdict 1 判 PASS 并带证据")
    public void testBinary_Pass() {
        when(llmJudgePort.complete(anyString())).thenReturn("verdict: 1\nevidence: 答案覆盖了标准答案的全部要点");
        RubricOutcome outcome = engine.execute(rubricOf(binaryDim("coverage", 1.0, "评估答案是否覆盖要点：{{reference}} 对比 {{answer}}")),
                "q", "标准答案", "实际答案", List.of("chunk1"));
        DimensionVerdict v = outcome.getDimensionVerdicts().get(0);
        assertEquals(RubricVerdict.PASS, v.getVerdict(), "合法 verdict 1 应判 PASS");
        assertTrue(v.getEvidence().contains("覆盖"), "证据应被解析: " + v.getEvidence());
        assertEquals(1.0, outcome.getWeightedScore(), "PASS 全权重综合分为 1");
        assertEquals(0.0, outcome.getUnknownRatio(), "无 unknown");
    }

    @Test
    @DisplayName("二元断言 — 合法输出 verdict 0 判 FAIL")
    public void testBinary_Fail() {
        when(llmJudgePort.complete(anyString())).thenReturn("verdict: 0\nevidence: 答案与参考资料无关");
        RubricOutcome outcome = engine.execute(rubricOf(binaryDim("faithful", 1.0, "答案是否忠实：{{answer}} 基于 {{context}}")),
                "q", "ref", "ans", List.of("c1"));
        assertEquals(RubricVerdict.FAIL, outcome.getDimensionVerdicts().get(0).getVerdict());
        assertEquals(0.0, outcome.getWeightedScore(), "FAIL 综合分为 0");
    }

    @Test
    @DisplayName("二元断言 — 非法输出归 unknown 而非失败")
    public void testBinary_IllegalOutputUnknown() {
        when(llmJudgePort.complete(anyString())).thenReturn("我觉得这个答案还行吧，不太好说。");
        RubricOutcome outcome = engine.execute(rubricOf(binaryDim("vague", 1.0, "评估：{{answer}}")),
                "q", "ref", "ans", List.of());
        assertEquals(RubricVerdict.UNKNOWN, outcome.getDimensionVerdicts().get(0).getVerdict(), "非法输出应归 unknown");
        assertEquals(1.0, outcome.getUnknownRatio(), "unknown 占比应为 1");
        assertEquals(0.0, outcome.getWeightedScore(), "全部 unknown 时综合分为 0");
    }

    @Test
    @DisplayName("二元断言 — 缺证据归 unknown")
    public void testBinary_MissingEvidenceUnknown() {
        when(llmJudgePort.complete(anyString())).thenReturn("verdict: 1");
        RubricOutcome outcome = engine.execute(rubricOf(binaryDim("noEvidence", 1.0, "评估：{{answer}}")),
                "q", "ref", "ans", List.of());
        assertEquals(RubricVerdict.UNKNOWN, outcome.getDimensionVerdicts().get(0).getVerdict(), "缺证据应归 unknown");
    }

    @Test
    @DisplayName("二元断言 — 中文结论/证据格式可解析")
    public void testBinary_ChineseFormat() {
        when(llmJudgePort.complete(anyString())).thenReturn("结论: 通过\n证据: 答案逐条对应标准答案要点");
        RubricOutcome pass = engine.execute(rubricOf(binaryDim("zh", 1.0, "评估：{{answer}}")), "q", "ref", "ans", List.of());
        assertEquals(RubricVerdict.PASS, pass.getDimensionVerdicts().get(0).getVerdict(), "中文「通过」应判 PASS");

        when(llmJudgePort.complete(anyString())).thenReturn("结论: 不通过\n证据: 关键要点缺失");
        RubricOutcome fail = engine.execute(rubricOf(binaryDim("zh2", 1.0, "评估：{{answer}}")), "q", "ref", "ans", List.of());
        assertEquals(RubricVerdict.FAIL, fail.getDimensionVerdicts().get(0).getVerdict(), "中文「不通过」应判 FAIL");
    }

    @Test
    @DisplayName("二元断言 — LLM 不可用（null）归 unknown 不抛异常")
    public void testBinary_LlmUnavailableUnknown() {
        when(llmJudgePort.complete(anyString())).thenReturn(null);
        RubricOutcome outcome = engine.execute(rubricOf(binaryDim("down", 1.0, "评估：{{answer}}")), "q", "ref", "ans", List.of());
        assertEquals(RubricVerdict.UNKNOWN, outcome.getDimensionVerdicts().get(0).getVerdict());
    }

    @Test
    @DisplayName("加权综合分 — unknown 维度剔除后按已知权重归一化")
    public void testWeightedScore_UnknownExcludedAndNormalized() {
        // 两维度：coverage(w=0.6) PASS；clarity(w=0.4) unknown → 综合 = 1.0*0.6/0.6 = 1.0
        when(llmJudgePort.complete(anyString()))
                .thenReturn("verdict: 1\nevidence: ok")
                .thenReturn("无法判断");
        RubricOutcome outcome = engine.execute(
                rubricOf(binaryDim("coverage", 0.6, "A"), binaryDim("clarity", 0.4, "B")),
                "q", "ref", "ans", List.of());
        assertEquals(1.0, outcome.getWeightedScore(), 1e-9, "unknown 剔除后归一化加权");
        assertEquals(0.5, outcome.getUnknownRatio(), "unknown 占比 1/2");
        assertEquals(1, outcome.getUnknownCount());
        assertEquals(2, outcome.getDimensionCount());
    }

    @Test
    @DisplayName("加权综合分 — PASS/FAIL 混合按权重加权")
    public void testWeightedScore_MixedVerdicts() {
        when(llmJudgePort.complete(anyString()))
                .thenReturn("verdict: 1\nevidence: ok")
                .thenReturn("verdict: 0\nevidence: bad");
        RubricOutcome outcome = engine.execute(
                rubricOf(binaryDim("d1", 0.7, "A"), binaryDim("d2", 0.3, "B")),
                "q", "ref", "ans", List.of());
        assertEquals(0.7, outcome.getWeightedScore(), 1e-9, "0.7*1 + 0.3*0 = 0.7");
    }

    @Test
    @DisplayName("连续分维度 — 解析 0-1 分数并计入综合分")
    public void testContinuousScore() {
        when(llmJudgePort.complete(anyString()))
                .thenReturn("0.8")
                .thenReturn("0.5");
        RubricEntity rubric = RubricEntity.builder().dimensions(List.of(
                RubricDimension.builder().key("faithfulness").weight(0.5).judgePrompt("P1").binary(false).build(),
                RubricDimension.builder().key("relevancy").weight(0.5).judgePrompt("P2").binary(false).build()
        )).build();
        RubricOutcome outcome = engine.execute(rubric, "q", "ref", "ans", List.of());
        assertEquals(0.8, outcome.getDimensionVerdicts().get(0).getScore(), "连续分应被解析");
        assertEquals(0.65, outcome.getWeightedScore(), 1e-9, "0.8*0.5+0.5*0.5");
        assertEquals(0.0, outcome.getUnknownRatio());
    }

    @Test
    @DisplayName("连续分维度 — 非法输出归 unknown")
    public void testContinuous_IllegalUnknown() {
        when(llmJudgePort.complete(anyString())).thenReturn("今天天气不错");
        RubricEntity rubric = RubricEntity.builder().dimensions(List.of(
                RubricDimension.builder().key("faithfulness").weight(1.0).judgePrompt("P").binary(false).build()
        )).build();
        RubricOutcome outcome = engine.execute(rubric, "q", "ref", "ans", List.of());
        assertEquals(RubricVerdict.UNKNOWN, outcome.getDimensionVerdicts().get(0).getVerdict());
        assertEquals(1.0, outcome.getUnknownRatio());
    }

    @Test
    @DisplayName("确定性维度 — 从 metricScores 注入，缺失归 unknown")
    public void testDeterministicDim() {
        RubricEntity rubric = rubricOf(
                RubricDimension.builder().key("f1").weight(0.5).binary(false).build(),
                RubricDimension.builder().key("mrr").weight(0.5).binary(false).build());
        // 只注入 f1，mrr 缺失
        RubricOutcome outcome = engine.execute(rubric, "q", "ref", "ans", List.of(), Map.of("f1", 0.8));
        assertEquals(0.8, outcome.getDimensionVerdicts().get(0).getScore());
        assertEquals(RubricVerdict.UNKNOWN, outcome.getDimensionVerdicts().get(1).getVerdict(), "未注入的确定性维度归 unknown");
        assertEquals(0.8, outcome.getWeightedScore(), 1e-9, "0.8*0.5/0.5 归一化");
        assertEquals(0.5, outcome.getUnknownRatio());
        // 确定性维度不应触发 LLM 调用
        verify(llmJudgePort, never()).complete(anyString());
    }

    @Test
    @DisplayName("prompt 渲染 — 维度模板占位符被替换（{{answer}} 等）")
    public void testPromptRendering() {
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(llmJudgePort.complete(captor.capture())).thenReturn("verdict: 1\nevidence: ok");
        engine.execute(rubricOf(binaryDim("render", 1.0, "问题：{{query}} 参考：{{reference}} 答案：{{answer}} 上下文：{{context}}")),
                "用户问", "标准答", "实际答", List.of("块1", "块2"));
        String prompt = captor.getValue();
        assertTrue(prompt.contains("用户问") && !prompt.contains("{{query}}"), "query 占位符应被替换");
        assertTrue(prompt.contains("标准答") && !prompt.contains("{{reference}}"));
        assertTrue(prompt.contains("实际答") && !prompt.contains("{{answer}}"));
        assertTrue(prompt.contains("块1") && prompt.contains("块2") && !prompt.contains("{{context}}"));
    }
}
