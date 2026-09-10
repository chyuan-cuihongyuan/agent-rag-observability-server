package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.RubricEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricDimension;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.EvalRubricMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.EvalRubricPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Rubric 仓储单元测试（工单 0133 R1）：内置种子幂等不变式（name 查重跳过 /
 * 并发唯一键冲突按已存在处理）+ PO/实体 dimensions JSON 互转。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Rubric 仓储测试")
public class RubricRepositoryTest {

    @Mock
    private EvalRubricMapper evalRubricMapper;

    @InjectMocks
    private RubricRepository rubricRepository;

    @Test
    @DisplayName("种子幂等 — name 已存在直接跳过不插入")
    public void testSeed_SkipExisting() {
        when(evalRubricMapper.selectByName("内置-答案质量评测标准"))
                .thenReturn(EvalRubricPO.builder().rubricId("builtin-answer-quality").build());
        RubricEntity entity = RubricEntity.builder()
                .rubricId("builtin-answer-quality").name("内置-答案质量评测标准")
                .dimensions(List.of(RubricDimension.builder().key("faithfulness").weight(0.25).build()))
                .build();
        assertFalse(rubricRepository.seedBuiltinIfAbsent(entity), "已存在应返回 false");
        verify(evalRubricMapper, never()).insert(any());
    }

    @Test
    @DisplayName("种子幂等 — 并发唯一键冲突按已存在处理")
    public void testSeed_ConcurrentConflict() {
        when(evalRubricMapper.selectByName("新种子")).thenReturn(null);
        doThrow(new DuplicateKeyException("uk_rubric_name"))
                .when(evalRubricMapper).insert(any(EvalRubricPO.class));
        RubricEntity entity = RubricEntity.builder().rubricId("r-1").name("新种子").build();
        assertFalse(rubricRepository.seedBuiltinIfAbsent(entity), "唯一键冲突应返回 false（幂等）");
    }

    @Test
    @DisplayName("种子 — 不存在时正常插入返回 true")
    public void testSeed_Insert() {
        when(evalRubricMapper.selectByName("新种子")).thenReturn(null);
        RubricEntity entity = RubricEntity.builder()
                .rubricId("r-1").name("新种子").evalType("TOOL_CALL")
                .dimensions(List.of(RubricDimension.builder().key("toolSelection").weight(0.5).build()))
                .build();
        assertTrue(rubricRepository.seedBuiltinIfAbsent(entity));
        verify(evalRubricMapper).insert(any(EvalRubricPO.class));
    }

    @Test
    @DisplayName("PO/实体互转 — dimensions JSON 解析回维度列表")
    public void testPoEntityConversion() {
        EvalRubricPO po = EvalRubricPO.builder()
                .rubricId("r-9").name("自定义").evalType("CONTEXT_QUALITY")
                .version(3).dimensions("[{\"key\":\"contextRecall\",\"weight\":0.4,\"binary\":false}]")
                .enabled(1).builtin(0).build();
        when(evalRubricMapper.selectByRubricId("r-9")).thenReturn(po);

        RubricEntity entity = rubricRepository.queryByRubricId("r-9");
        assertNotNull(entity);
        assertEquals(1, entity.getDimensions().size());
        assertEquals("contextRecall", entity.getDimensions().get(0).getKey());
        assertEquals(0.4, entity.getDimensions().get(0).getWeight());
        assertTrue(entity.getEnabled());
        assertFalse(entity.getBuiltin());
    }

    @Test
    @DisplayName("PO/实体互转 — 非法 dimensions 降级为 null 维度不抛异常")
    public void testPoEntityConversion_IllegalJson() {
        EvalRubricPO po = EvalRubricPO.builder().rubricId("r-bad").name("坏数据")
                .dimensions("{broken").build();
        when(evalRubricMapper.selectByRubricId("r-bad")).thenReturn(po);
        RubricEntity entity = rubricRepository.queryByRubricId("r-bad");
        assertNotNull(entity, "实体仍应返回");
        assertNull(entity.getDimensions(), "非法 JSON 维度降级为 null");
    }
}
