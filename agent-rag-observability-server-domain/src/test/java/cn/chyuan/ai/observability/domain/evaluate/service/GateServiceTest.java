package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateRecordEntity;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 门禁规则服务测试（工单 0136 R4 验收）— CRUD 校验：
 * JSON 结构 / 维度存在性 / 阈值范围 / trials 上下限 / 重名拒绝 / 删除=停用 / 记录查询委托。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("门禁规则 CRUD 校验测试")
class GateServiceTest {

    @Mock
    private IGateRepository gateRepository;
    @Mock
    private IGateRecordRepository gateRecordRepository;

    @InjectMocks
    private GateService gateService;

    private GateEntity.GateEntityBuilder builder() {
        return GateEntity.builder()
                .name("发布门禁")
                .safetyDimsJson("{\"hallucination\":0.8}")
                .scoreThresholdsJson("{\"overall\":0.6,\"passRate\":0.5}")
                .trials(3);
    }

    // ===== 创建 =====

    @Test
    @DisplayName("创建成功 — JSON 解析入实体、gateId 生成、trials/enabled 兜底")
    public void testCreateSuccess() {
        when(gateRepository.queryByName("发布门禁")).thenReturn(null);
        GateEntity created = gateService.create(builder().build());

        assertNotNull(created.getGateId(), "应生成 gateId");
        assertEquals(Map.of("hallucination", 0.8), created.getSafetyDims());
        assertEquals(Map.of("overall", 0.6, "passRate", 0.5), created.getScoreThresholds());
        assertEquals(3, created.getTrials());
        assertTrue(created.getEnabled(), "enabled 缺省 true");
        verify(gateRepository).insert(created);
    }

    @Test
    @DisplayName("创建校验 — 非法 JSON 结构化拒绝")
    public void testCreateIllegalJson() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> gateService.create(builder().safetyDimsJson("{not-json").build()));
        assertTrue(ex.getMessage().contains("非法 JSON"), ex.getMessage());
        verify(gateRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建校验 — 阈值非数值结构化拒绝")
    public void testCreateNonNumericValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> gateService.create(builder().safetyDimsJson("{\"hallucination\":\"高\"}").build()));
        assertTrue(ex.getMessage().contains("必须是数值"), ex.getMessage());
    }

    @Test
    @DisplayName("创建校验 — 阈值越界 [0,1] 拒绝")
    public void testCreateValueOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> gateService.create(builder().scoreThresholdsJson("{\"overall\":1.5}").build()));
        assertThrows(IllegalArgumentException.class,
                () -> gateService.create(builder().scoreThresholdsJson("{\"overall\":-0.1}").build()));
    }

    @Test
    @DisplayName("创建校验 — safetyDims 维度不存在拒绝（Rubric 维度 key 全集外）")
    public void testCreateUnknownSafetyDim() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> gateService.create(builder().safetyDimsJson("{\"notADim\":0.8}").build()));
        assertTrue(ex.getMessage().contains("维度不存在"), ex.getMessage());
    }

    @Test
    @DisplayName("创建校验 — scoreThresholds 允许 overall/passRate 汇总 key，但 safetyDims 不允许")
    public void testSummaryKeyOnlyForScore() {
        // scoreThresholds 允许 passRate（合法）
        when(gateRepository.queryByName(anyString())).thenReturn(null);
        assertDoesNotThrow(() -> gateService.create(GateEntity.builder()
                .name("仅通过率").scoreThresholdsJson("{\"passRate\":0.9}").build()));
        // safetyDims 不允许 passRate（安全维度必须是真实评测维度）
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> gateService.create(builder().safetyDimsJson("{\"passRate\":0.9}").build()));
        assertTrue(ex.getMessage().contains("维度不存在"), ex.getMessage());
    }

    @Test
    @DisplayName("创建校验 — 两层规则同时为空拒绝（门禁至少一条规则）")
    public void testCreateBothEmptyRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> gateService.create(GateEntity.builder().name("空门禁").build()));
        assertTrue(ex.getMessage().contains("不能同时为空"), ex.getMessage());
    }

    @Test
    @DisplayName("创建校验 — trials 越上下限拒绝（1-20，防 LLM judge 成本爆炸）")
    public void testCreateTrialsBounds() {
        when(gateRepository.queryByName(anyString())).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> gateService.create(builder().trials(0).build()));
        assertThrows(IllegalArgumentException.class,
                () -> gateService.create(builder().trials(21).build()));
        assertDoesNotThrow(() -> gateService.create(builder().trials(20).build()));
    }

    @Test
    @DisplayName("创建校验 — 重名拒绝")
    public void testCreateDuplicateName() {
        when(gateRepository.queryByName("发布门禁")).thenReturn(GateEntity.builder().gateId("g-old").build());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> gateService.create(builder().build()));
        assertTrue(ex.getMessage().contains("已存在"), ex.getMessage());
    }

    @Test
    @DisplayName("创建校验 — name 为空拒绝")
    public void testCreateBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> gateService.create(builder().name(" ").build()));
    }

    // ===== 更新 =====

    @Test
    @DisplayName("更新 — 缺省字段继承既有配置（JSON 不传沿用，enabled 不传不变）")
    public void testUpdateMergeExisting() {
        GateEntity existing = GateEntity.builder()
                .gateId("gate-1").name("发布门禁").trials(5).enabled(true)
                .safetyDimsJson("{\"hallucination\":0.8}")
                .scoreThresholdsJson("{\"overall\":0.6}")
                .build();
        when(gateRepository.queryByGateId("gate-1")).thenReturn(existing);

        gateService.update(GateEntity.builder().gateId("gate-1").trials(2).build());

        ArgumentCaptor<GateEntity> captor = ArgumentCaptor.forClass(GateEntity.class);
        verify(gateRepository).update(captor.capture());
        assertEquals("发布门禁", captor.getValue().getName(), "name 缺省继承");
        assertEquals(Map.of("hallucination", 0.8), captor.getValue().getSafetyDims(), "safety 缺省继承");
        assertEquals(2, captor.getValue().getTrials(), "trials 显式覆盖");
        assertTrue(captor.getValue().getEnabled());
    }

    @Test
    @DisplayName("更新 — 不存在的 gate 拒绝；改名撞名拒绝")
    public void testUpdateNotFoundAndRenameConflict() {
        when(gateRepository.queryByGateId("g-none")).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> gateService.update(GateEntity.builder().gateId("g-none").name("x").build()));

        GateEntity existing = GateEntity.builder().gateId("gate-1").name("A")
                .safetyDimsJson("{\"f1\":0.5}").trials(1).enabled(true).build();
        when(gateRepository.queryByGateId("gate-1")).thenReturn(existing);
        when(gateRepository.queryByName("B")).thenReturn(GateEntity.builder().gateId("gate-2").name("B").build());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> gateService.update(GateEntity.builder().gateId("gate-1").name("B").build()));
        assertTrue(ex.getMessage().contains("已存在"), ex.getMessage());
    }

    // ===== 删除（=停用） =====

    @Test
    @DisplayName("删除 — 以停用代替物理删除（历史记录可追溯）")
    public void testDeleteDisables() {
        GateEntity existing = GateEntity.builder().gateId("gate-1").name("发布门禁").enabled(true).build();
        when(gateRepository.queryByGateId("gate-1")).thenReturn(existing);

        gateService.delete("gate-1");

        ArgumentCaptor<GateEntity> captor = ArgumentCaptor.forClass(GateEntity.class);
        verify(gateRepository).update(captor.capture());
        assertFalse(captor.getValue().getEnabled(), "删除后 enabled=false");
        when(gateRepository.queryByGateId("g-none")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> gateService.delete("g-none"));
    }

    // ===== 门禁记录查询委托 =====

    @Test
    @DisplayName("记录查询 — 最新/历史/按任务委托仓储（gateId 可空查全部）")
    public void testRecordQueryDelegation() {
        when(gateRecordRepository.queryLatestByGateId("gate-1")).thenReturn(GateRecordEntity.builder().recordId("r-1").build());
        assertEquals("r-1", gateService.queryLatestRecord("gate-1").getRecordId());

        when(gateRecordRepository.queryList(isNull(), eq(1), eq(20))).thenReturn(List.of());
        gateService.queryRecordList(null, 1, 20);
        verify(gateRecordRepository).queryList(isNull(), eq(1), eq(20));

        when(gateRecordRepository.queryByTaskId("task-1")).thenReturn(null);
        assertNull(gateService.queryRecordByTaskId("task-1"));
        verify(gateRecordRepository).queryByTaskId("task-1");
    }
}
