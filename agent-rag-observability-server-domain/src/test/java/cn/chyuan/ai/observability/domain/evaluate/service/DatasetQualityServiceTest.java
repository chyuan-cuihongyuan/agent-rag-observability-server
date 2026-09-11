package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.EvalDatasetItem;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据集质量守护单元测试（工单 0172 X3）— 重复检测/相似对/长度分布/覆盖率/空集。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("数据集质量守护测试")
class DatasetQualityServiceTest {

    private final DatasetQualityService service = new DatasetQualityService();

    private EvalDatasetEntity datasetOf(String itemsJson) {
        return EvalDatasetEntity.builder().datasetId("ds-1").itemsJson(itemsJson).build();
    }

    @Test
    @DisplayName("重复与覆盖率 — 归一化精确重复计数、字段覆盖率")
    public void testDuplicatesAndCoverage() {
        String items = JSON.toJSONString(List.of(
                EvalDatasetItem.builder().prompt("加油站 营业时间").standardAnswer("24小时").traceId("t-1").build(),
                EvalDatasetItem.builder().prompt("加油站营业时间").standardAnswer("24小时").build(),
                EvalDatasetItem.builder().prompt("柴油价格").build()
        ));

        Map<String, Object> report = service.analyze(datasetOf(items));

        assertEquals(3, report.get("total"));
        assertEquals(1, report.get("exactDuplicateGroups"));
        assertEquals(1, report.get("exactDuplicateItems"));
        assertEquals(2.0 / 3, (Double) report.get("answerCoverage"), 1e-3);
        assertEquals(1.0 / 3, (Double) report.get("traceCoverage"), 1e-3);
    }

    @Test
    @DisplayName("相似对 — Jaccard ≥0.8 非重复对检出")
    public void testSimilarPairs() {
        String items = JSON.toJSONString(List.of(
                EvalDatasetItem.builder().prompt("如何办理加油卡业务").build(),
                EvalDatasetItem.builder().prompt("如何办理加油卡业务流程").build(),
                EvalDatasetItem.builder().prompt("今天天气怎么样").build()
        ));

        Map<String, Object> report = service.analyze(datasetOf(items));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pairs = (List<Map<String, Object>>) report.get("similarPairs");
        assertEquals(1, pairs.size());
        assertTrue((Double) pairs.get(0).get("jaccard") >= 0.8);
    }

    @Test
    @DisplayName("长度分布 — min/max/mean/p95 与空集 null")
    public void testLengthDistribution() {
        String items = JSON.toJSONString(List.of(
                EvalDatasetItem.builder().query("ab").build(),
                EvalDatasetItem.builder().query("abcd").build(),
                EvalDatasetItem.builder().query("abcdef").build(),
                EvalDatasetItem.builder().query("abcdefgh").build()
        ));

        Map<String, Object> report = service.analyze(datasetOf(items));
        @SuppressWarnings("unchecked")
        Map<String, Object> dist = (Map<String, Object>) report.get("queryLength");
        assertEquals(2, dist.get("min"));
        assertEquals(8, dist.get("max"));

        // 空数据集：长度分布 null
        Map<String, Object> empty = service.analyze(datasetOf("[]"));
        @SuppressWarnings("unchecked")
        Map<String, Object> emptyDist = (Map<String, Object>) empty.get("queryLength");
        assertNull(emptyDist.get("min"));
        assertEquals(0, empty.get("total"));
    }

    @Test
    @DisplayName("纯函数 — jaccard/tokens/normalize 边界")
    public void testPureHelpers() {
        assertEquals(1.0, DatasetQualityService.jaccard(Set.of(), Set.of()), 1e-9);
        assertEquals(1.0 / 3, DatasetQualityService.jaccard(Set.of("a", "b"), Set.of("b", "c")), 1e-9);
        assertEquals("abc", DatasetQualityService.normalize(" A B\tC "));
        assertTrue(DatasetQualityService.tokens("加油").contains("加"));
        assertTrue(DatasetQualityService.tokens("hello world").contains("hello"));
    }
}
