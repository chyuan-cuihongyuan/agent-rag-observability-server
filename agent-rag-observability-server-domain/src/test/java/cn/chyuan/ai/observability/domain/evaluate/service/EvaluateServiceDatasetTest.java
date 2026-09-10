package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 评测集资产化单元测试（工单 0134 R2 验收）：
 * 冻结不变式（frozen 版本条目不可改）、三池筛选（含 NULL 未分类兼容）、
 * 版本快照复制、Task 三元组字段经实体贯通。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("评测集版本化/三池/冻结测试")
public class EvaluateServiceDatasetTest {

    @Mock
    private IEvalTaskRepository evalTaskRepository;
    @Mock
    private IEvalResultRepository evalResultRepository;
    @Mock
    private IEvalDatasetRepository evalDatasetRepository;

    @InjectMocks
    private EvaluateService evaluateService;

    private EvalDatasetEntity dataset(String datasetId, String name, int version, Boolean frozen,
                                      String pool, String source, String itemsJson) {
        return EvalDatasetEntity.builder()
                .datasetId(datasetId).datasetName(name).description("desc")
                .itemCount(3).itemsJson(itemsJson).version(version)
                .pool(pool).source(source).frozen(frozen)
                .build();
    }

    // ===== 冻结不变式 =====

    @Test
    @DisplayName("冻结不变式 — frozen 版本保存条目被拒绝且不触达仓储")
    public void testSaveDataset_FrozenRejected() {
        when(evalDatasetRepository.queryByDatasetId("ds-frozen"))
                .thenReturn(dataset("ds-frozen", "黄金集", 2, true, "golden", "manual", "[...]"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> evaluateService.saveDataset(EvalDatasetEntity.builder()
                        .datasetId("ds-frozen").itemsJson("[{modified:true}]").build()));
        assertTrue(ex.getMessage().contains("已冻结"), "错误信息应说明冻结: " + ex.getMessage());
        verify(evalDatasetRepository, never()).update(any());
        verify(evalDatasetRepository, never()).save(any());
    }

    @Test
    @DisplayName("未冻结版本 — 条目更新走 update 并继承版本锚字段")
    public void testSaveDataset_UnfrozenUpdates() {
        when(evalDatasetRepository.queryByDatasetId("ds-edit"))
                .thenReturn(dataset("ds-edit", "挑战集", 3, false, "challenge", "trace", "[old]"));

        evaluateService.saveDataset(EvalDatasetEntity.builder()
                .datasetId("ds-edit").itemsJson("[new]").itemCount(1).build());

        ArgumentCaptor<EvalDatasetEntity> captor = ArgumentCaptor.forClass(EvalDatasetEntity.class);
        verify(evalDatasetRepository).update(captor.capture());
        assertEquals("[new]", captor.getValue().getItemsJson());
        assertEquals("挑战集", captor.getValue().getDatasetName(), "name 为版本锚不被条目更新改变");
        assertEquals(3, captor.getValue().getVersion(), "version 不被条目更新改变");
        assertEquals("challenge", captor.getValue().getPool(), "未显式指定时 pool 继承");
        verify(evalDatasetRepository, never()).save(any());
    }

    @Test
    @DisplayName("新建数据集 — version 默认 1、frozen 默认 false")
    public void testSaveDataset_CreateDefaults() {
        // datasetId 为空 → 直接走新建分支
        evaluateService.saveDataset(EvalDatasetEntity.builder()
                .datasetName("新数据集").itemsJson("[]").pool("golden").source("seed").build());
        ArgumentCaptor<EvalDatasetEntity> captor = ArgumentCaptor.forClass(EvalDatasetEntity.class);
        verify(evalDatasetRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getVersion());
        assertFalse(captor.getValue().getFrozen());
        assertEquals("golden", captor.getValue().getPool());
    }

    // ===== 三池筛选 =====

    @Test
    @DisplayName("三池筛选 — golden/challenge/wrong 透传仓储（page 语义与 queryList 一致）")
    public void testQueryByPool() {
        when(evalDatasetRepository.queryByPool(eq("golden"), anyInt(), anyInt())).thenReturn(List.of());
        evaluateService.queryDatasetByPool("golden", 1, 20);
        verify(evalDatasetRepository).queryByPool("golden", 1, 20);

        when(evalDatasetRepository.queryByPool(eq("wrong"), anyInt(), anyInt())).thenReturn(List.of());
        evaluateService.queryDatasetByPool("WRONG", 1, 20);
        // 池名应归一化小写透传
        verify(evalDatasetRepository).queryByPool("wrong", 1, 20);
    }

    @Test
    @DisplayName("三池筛选 — unclassified 映射 pool IS NULL（存量 NULL 兼容）")
    public void testQueryByPool_UnclassifiedAsNull() {
        when(evalDatasetRepository.queryByPool(isNull(), anyInt(), anyInt())).thenReturn(List.of());
        evaluateService.queryDatasetByPool("unclassified", 1, 20);
        // unclassified 应翻译为 null（mapper 侧翻译为 pool IS NULL 查询）
        verify(evalDatasetRepository).queryByPool(isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("三池筛选 — 非法池名结构化拒绝")
    public void testQueryByPool_Illegal() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> evaluateService.queryDatasetByPool("diamond", 1, 20));
        assertTrue(ex.getMessage().contains("非法样本池"), ex.getMessage());
        verify(evalDatasetRepository, never()).queryByPool(any(), anyInt(), anyInt());
    }

    // ===== 版本化 =====

    @Test
    @DisplayName("版本快照复制 — 条目原样复制、version 同名递增、新版本未冻结")
    public void testCopyVersion() {
        EvalDatasetEntity src = dataset("ds-v2", "黄金集", 2, true, "golden", "manual",
                "[{\"query\":\"q1\",\"prompt\":\"P\",\"promptVersion\":\"v1\",\"traceId\":\"t-1\"}]");
        when(evalDatasetRepository.queryByDatasetId("ds-v2")).thenReturn(src);
        when(evalDatasetRepository.maxVersion("黄金集")).thenReturn(2);

        EvalDatasetEntity copy = evaluateService.copyDatasetVersion("ds-v2");

        ArgumentCaptor<EvalDatasetEntity> captor = ArgumentCaptor.forClass(EvalDatasetEntity.class);
        verify(evalDatasetRepository).save(captor.capture());
        assertEquals(3, copy.getVersion(), "新版本 = maxVersion+1");
        assertEquals("黄金集", copy.getDatasetName(), "版本随名称递增");
        assertEquals(src.getItemsJson(), copy.getItemsJson(), "条目快照原样复制（含 Task 三元组字段）");
        assertFalse(copy.getFrozen(), "新版本默认未冻结");
        assertEquals("golden", copy.getPool(), "池与来源继承");
        assertEquals("manual", copy.getSource());
    }

    @Test
    @DisplayName("版本快照复制 — 源不存在结构化拒绝")
    public void testCopyVersion_NotFound() {
        when(evalDatasetRepository.queryByDatasetId("ds-none")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> evaluateService.copyDatasetVersion("ds-none"));
        verify(evalDatasetRepository, never()).save(any());
    }

    @Test
    @DisplayName("版本列表 — 按名称查同名全部版本")
    public void testQueryVersions() {
        when(evalDatasetRepository.queryByDatasetId("ds-1"))
                .thenReturn(dataset("ds-1", "错题集", 1, false, "wrong", "trace", "[]"));
        when(evalDatasetRepository.queryVersions("错题集")).thenReturn(List.of(
                dataset("ds-2", "错题集", 2, false, "wrong", "trace", "[]"),
                dataset("ds-1", "错题集", 1, false, "wrong", "trace", "[]")));

        List<EvalDatasetEntity> versions = evaluateService.queryDatasetVersions("ds-1");
        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersion(), "版本号降序在前");
        verify(evalDatasetRepository).queryVersions("错题集");
    }

    // ===== 冻结/解冻 =====

    @Test
    @DisplayName("冻结/解冻 — 委托仓储置位，不存在结构化拒绝")
    public void testFreezeUnfreeze() {
        when(evalDatasetRepository.queryByDatasetId("ds-x"))
                .thenReturn(dataset("ds-x", "黄金集", 1, false, null, null, "[]"));
        evaluateService.freezeDataset("ds-x", true);
        verify(evalDatasetRepository).updateFrozen("ds-x", true);

        evaluateService.freezeDataset("ds-x", false);
        verify(evalDatasetRepository).updateFrozen("ds-x", false);

        when(evalDatasetRepository.queryByDatasetId("ds-none")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> evaluateService.freezeDataset("ds-none", true));
    }
}
