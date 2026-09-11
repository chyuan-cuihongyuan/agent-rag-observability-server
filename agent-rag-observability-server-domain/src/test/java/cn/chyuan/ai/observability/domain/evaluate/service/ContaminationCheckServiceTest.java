package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 池间污染检查单元测试（工单 0173）— 精确/相似/空池/BLOCK 阈值。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("池间污染检查测试")
class ContaminationCheckServiceTest {

    @Mock
    private IEvalDatasetRepository evalDatasetRepository;

    @InjectMocks
    private ContaminationCheckService service;

    @Test
    @DisplayName("分析纯函数 — 精确重叠、BLOCK 阈值判定")
    public void testAnalyze() {
        Set<String> a = Set.of("q1", "q2", "q3", "q4");
        Set<String> b = Set.of("q4", "q9");

        Map<String, Object> result = service.analyze(a, b, 0.2);

        assertEquals(4, result.get("queriesA"));
        assertEquals(1, result.get("overlapExact"));
        assertEquals(0, result.get("overlapSimilar"));
        assertEquals(0.25, (Double) result.get("ratioA"), 1e-9);
        assertEquals(true, result.get("block")); // 0.25 > 0.2
    }

    @Test
    @DisplayName("空池 — ratio 0 不触发 BLOCK")
    public void testEmptyPool() {
        Map<String, Object> result = service.analyze(Set.of(), Set.of("x"), 0.05);
        assertEquals(0.0, (Double) result.get("ratioA"), 1e-9);
        assertEquals(false, result.get("block"));
    }

    @Test
    @DisplayName("poolQueries — 池内全部数据集条目归一化并入集合（prompt 优先）")
    public void testPoolQueries() {
        when(evalDatasetRepository.queryByPool(eq("golden"), anyInt(), anyInt())).thenReturn(List.of(
                EvalDatasetEntity.builder().itemsJson(
                        "[{\"prompt\":\"Prompt One\"},{\"query\":\"Query Two\"}]").build(),
                EvalDatasetEntity.builder().itemsJson("not-json").build()
        ));

        Set<String> queries = service.poolQueries("golden");

        assertEquals(2, queries.size());
        assertTrue(queries.contains("promptone"));
        assertTrue(queries.contains("querytwo"));
    }
}
