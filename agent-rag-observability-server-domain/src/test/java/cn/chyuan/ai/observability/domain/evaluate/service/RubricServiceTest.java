package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IRubricRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.RubricEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricDimension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Rubric 服务单元测试（工单 0133 R1 验收）：
 * 1. CRUD 校验：维度 key 重复 / 权重和≠1 / 非法 JSON 结构化拒绝 / 重名拒绝 / 内置不可改删
 * 2. 内置种子幂等：懒加载只种一次、name 已存在自动跳过
 * 3. 权重解析：仓储异常回退内置 v2 兜底口径
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Rubric 服务测试")
public class RubricServiceTest {

    @Mock
    private IRubricRepository rubricRepository;

    @InjectMocks
    private RubricService rubricService;

    private static String dims(String... jsonItems) {
        return "[" + String.join(",", jsonItems) + "]";
    }

    @Test
    @DisplayName("创建 Rubric — 合法维度通过校验并落库")
    public void testCreate_Valid() {
        when(rubricRepository.queryByName("答案质量-v3")).thenReturn(null);
        String dimensionsJson = dims(
                "{\"key\":\"faithfulness\",\"label\":\"忠实度\",\"weight\":0.6,\"judgePrompt\":\"模板A\",\"binary\":true}",
                "{\"key\":\"relevancy\",\"label\":\"相关性\",\"weight\":0.4,\"judgePrompt\":\"模板B\",\"binary\":true}");

        RubricEntity entity = RubricEntity.builder()
                .name("答案质量-v3").evalType("ANSWER_QUALITY")
                .dimensionsJson(dimensionsJson).build();

        RubricEntity created = rubricService.create(entity);

        assertNotNull(created.getRubricId(), "应生成 rubricId");
        assertEquals(1, created.getVersion(), "版本默认 1");
        assertTrue(created.getEnabled(), "默认启用");
        assertFalse(created.getBuiltin(), "自建非内置");
        assertEquals(2, created.getDimensions().size(), "维度应解析为 2 个");
        verify(rubricRepository).insert(any(RubricEntity.class));
    }

    @Test
    @DisplayName("创建 Rubric — 维度 key 重复拒绝")
    public void testCreate_DuplicateKey() {
        String dimensionsJson = dims(
                "{\"key\":\"faithfulness\",\"weight\":0.5}",
                "{\"key\":\"faithfulness\",\"weight\":0.5}");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rubricService.create(RubricEntity.builder()
                        .name("重复key").dimensionsJson(dimensionsJson).build()));
        assertTrue(ex.getMessage().contains("维度 key 重复: faithfulness"), "错误信息应指明重复 key: " + ex.getMessage());
        verify(rubricRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建 Rubric — 权重和≠1 拒绝（偏小/偏大）")
    public void testCreate_WeightSumNotOne() {
        String tooSmall = dims("{\"key\":\"a\",\"weight\":0.4}", "{\"key\":\"b\",\"weight\":0.5}");
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> rubricService.create(RubricEntity.builder()
                        .name("权重不足").dimensionsJson(tooSmall).build()));
        assertTrue(ex1.getMessage().contains("权重和必须为 1"), ex1.getMessage());

        String tooBig = dims("{\"key\":\"a\",\"weight\":0.6}", "{\"key\":\"b\",\"weight\":0.6}");
        assertThrows(IllegalArgumentException.class,
                () -> rubricService.create(RubricEntity.builder()
                        .name("权重超出").dimensionsJson(tooBig).build()));
        verify(rubricRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建 Rubric — 非法 JSON 结构化拒绝")
    public void testCreate_IllegalJson() {
        assertThrows(IllegalArgumentException.class, () -> rubricService.create(RubricEntity.builder()
                .name("非法json").dimensionsJson("{not-json]").build()));
        // 非数组元素缺 key 同样结构化拒绝
        assertThrows(IllegalArgumentException.class, () -> rubricService.create(RubricEntity.builder()
                .name("缺key").dimensionsJson("[{\"label\":\"无key\",\"weight\":1.0}]").build()));
        // 空数组拒绝
        assertThrows(IllegalArgumentException.class, () -> rubricService.create(RubricEntity.builder()
                .name("空数组").dimensionsJson("[]").build()));
        verify(rubricRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建 Rubric — 重名拒绝")
    public void testCreate_DuplicateName() {
        when(rubricRepository.queryByName("已有名称")).thenReturn(RubricEntity.builder().rubricId("r-1").build());
        assertThrows(IllegalArgumentException.class, () -> rubricService.create(RubricEntity.builder()
                .name("已有名称").dimensionsJson(dims("{\"key\":\"a\",\"weight\":1.0}")).build()));
        verify(rubricRepository, never()).insert(any());
    }

    @Test
    @DisplayName("更新 Rubric — 内置种子不可修改")
    public void testUpdate_BuiltinRejected() {
        when(rubricRepository.queryByRubricId("builtin-1")).thenReturn(RubricEntity.builder()
                .rubricId("builtin-1").name("内置-RAG检索评测标准").builtin(true).build());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rubricService.update(RubricEntity.builder()
                        .rubricId("builtin-1").dimensionsJson(dims("{\"key\":\"a\",\"weight\":1.0}")).build()));
        assertTrue(ex.getMessage().contains("内置 Rubric 不可修改"), ex.getMessage());
    }

    @Test
    @DisplayName("更新 Rubric — 自建可更新且校验维度")
    public void testUpdate_CustomOk() {
        when(rubricRepository.queryByRubricId("r-9")).thenReturn(RubricEntity.builder()
                .rubricId("r-9").name("旧名").evalType("ANSWER_QUALITY").builtin(false).enabled(true).build());
        rubricService.update(RubricEntity.builder()
                .rubricId("r-9").name("新名").version(2)
                .dimensionsJson(dims("{\"key\":\"faithfulness\",\"weight\":1.0}")).build());
        ArgumentCaptor<RubricEntity> captor = ArgumentCaptor.forClass(RubricEntity.class);
        verify(rubricRepository).update(captor.capture());
        assertEquals("新名", captor.getValue().getName());
        assertEquals(2, captor.getValue().getVersion());
    }

    @Test
    @DisplayName("内置种子 — 懒加载首次种 5 份，再次调用进程内幂等")
    public void testEnsureBuiltin_Idempotent() {
        when(rubricRepository.seedBuiltinIfAbsent(any())).thenReturn(true);
        rubricService.ensureBuiltin();
        verify(rubricRepository, times(5)).seedBuiltinIfAbsent(any(RubricEntity.class));

        // 第二次调用：进程内短路，不再触发仓储
        clearInvocations(rubricRepository);
        rubricService.ensureBuiltin();
        verify(rubricRepository, never()).seedBuiltinIfAbsent(any());
    }

    @Test
    @DisplayName("内置种子 — name 已存在时跳过（幂等，不重复插入）")
    public void testEnsureBuiltin_SkipExisting() {
        when(rubricRepository.seedBuiltinIfAbsent(any())).thenReturn(false);
        assertDoesNotThrow(() -> rubricService.ensureBuiltin());
        verify(rubricRepository, times(5)).seedBuiltinIfAbsent(any(RubricEntity.class));
        // 全部跳过：无异常、无重复插入（seedBuiltinIfAbsent 语义由仓储保证 name 查重）
    }

    @Test
    @DisplayName("内置种子 — 仓储异常降级不阻断")
    public void testEnsureBuiltin_RepositoryFailure() {
        when(rubricRepository.seedBuiltinIfAbsent(any())).thenThrow(new RuntimeException("表未建"));
        assertDoesNotThrow(() -> rubricService.ensureBuiltin());
    }

    @Test
    @DisplayName("权重解析 — evalType 命中启用中 Rubric 时取其维度权重")
    public void testResolveWeights_FromRubric() {
        RubricEntity rubric = RubricEntity.builder()
                .name("自定义答案质量").evalType("ANSWER_QUALITY").enabled(true)
                .dimensions(List.of(
                        RubricDimension.builder().key("faithfulness").weight(0.7).build(),
                        RubricDimension.builder().key("relevancy").weight(0.3).build()))
                .build();
        when(rubricRepository.queryEnabledByEvalType("ANSWER_QUALITY")).thenReturn(rubric);
        when(rubricRepository.seedBuiltinIfAbsent(any())).thenReturn(false);

        Map<String, Double> weights = rubricService.resolveWeights("ANSWER_QUALITY");
        assertEquals(0.7, weights.get("faithfulness"));
        assertEquals(0.3, weights.get("relevancy"));
        verify(rubricRepository).queryEnabledByEvalType("ANSWER_QUALITY");
    }

    @Test
    @DisplayName("权重解析 — 仓储异常回退内置 v2 兜底口径（五类型）")
    public void testResolveWeights_Fallback() {
        when(rubricRepository.queryEnabledByEvalType(anyString())).thenThrow(new RuntimeException("库不可用"));

        Map<String, Double> rag = rubricService.resolveWeights("RAG_RETRIEVAL");
        assertEquals(0.4, rag.get("f1"));
        assertEquals(0.2, rag.get("mrr"));

        Map<String, Double> answer = rubricService.resolveWeights("ANSWER_QUALITY");
        assertEquals(0.25, answer.get("faithfulness"));
        assertEquals(0.1, answer.get("hallucination"));

        Map<String, Double> context = rubricService.resolveWeights("CONTEXT_QUALITY");
        assertEquals(0.4, context.get("contextPrecision"));

        Map<String, Double> tool = rubricService.resolveWeights("TOOL_CALL");
        assertEquals(0.5, tool.get("toolSelection"));

        Map<String, Double> decision = rubricService.resolveWeights("AGENT_DECISION");
        assertEquals(0.4, decision.get("reasoning"));

        // 未知类型默认口径（平铺展开和为 1）
        Map<String, Double> unknown = rubricService.resolveWeights("SOMETHING_ELSE");
        assertEquals(1.0, unknown.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    @DisplayName("评判模板解析 — 内置 9 维始终可用（仓储缺失用内置兜底补齐）")
    public void testResolveJudgePromptTemplates_AlwaysNine() {
        when(rubricRepository.queryList(anyInt(), anyInt())).thenThrow(new RuntimeException("库不可用"));
        when(rubricRepository.seedBuiltinIfAbsent(any())).thenThrow(new RuntimeException("表未建"));

        Map<String, String> templates = rubricService.resolveJudgePromptTemplates();
        assertEquals(9, templates.size(), "9 个 LLM 评判维度模板齐备");
        assertTrue(templates.containsKey("faithfulness"));
        assertTrue(templates.containsKey("answerCorrectness"));
        assertTrue(templates.get("contextPrecision").contains("{{numberedContext}}"));
    }
}
