package cn.chyuan.ai.observability.domain.insight.service;

import cn.chyuan.ai.observability.domain.insight.adapter.repository.ITraceAnnotationRepository;
import cn.chyuan.ai.observability.domain.insight.model.entity.TraceAnnotationEntity;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 人工注解服务单元测试（工单 0150 U4）— 校验/留痕/upsert 重评即改判/分页钳制。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("人工注解服务测试")
class TraceAnnotationServiceTest {

    @Mock
    private ITraceAnnotationRepository traceAnnotationRepository;

    @InjectMocks
    private TraceAnnotationService service;

    @Test
    @DisplayName("保存 — 校验 1-5 与 traceId 非空，operator 空归 unknown，留痕时间生成")
    public void testSaveValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.save(null, 3, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.save("t-1", 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.save("t-1", 6, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.save("t-1", null, null, null));

        when(traceAnnotationRepository.queryByTraceAndOperator("t-1", "unknown")).thenReturn(null);
        TraceAnnotationEntity saved = service.save("t-1", 4, "回答不错", "  ");

        assertEquals(4, saved.getScore());
        assertEquals("unknown", saved.getOperator());
        assertNotNull(saved.getCreateTime());
        verify(traceAnnotationRepository).upsert(any(TraceAnnotationEntity.class));
    }

    @Test
    @DisplayName("重评即改判 — 既有注解复用 id 与 createTime")
    public void testUpsertOverwrite() {
        TraceAnnotationEntity existing = TraceAnnotationEntity.builder()
                .id(7L).traceId("t-1").score(2).operator("alice").createTime("2026-09-10 09:00:00").build();
        when(traceAnnotationRepository.queryByTraceAndOperator("t-1", "alice")).thenReturn(existing);

        service.save("t-1", 5, "复核后改判", "alice");

        ArgumentCaptor<TraceAnnotationEntity> captor = ArgumentCaptor.forClass(TraceAnnotationEntity.class);
        verify(traceAnnotationRepository).upsert(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals(5, captor.getValue().getScore());
        assertEquals("2026-09-10 09:00:00", captor.getValue().getCreateTime());
    }

    @Test
    @DisplayName("查询 — 分页钳制与 score 筛选透传")
    public void testQueryList() {
        when(traceAnnotationRepository.queryList(any(), anyInt(), anyInt())).thenReturn(List.of());

        service.queryList(3, 0, 500);
        service.queryList(null, 1, 20);

        verify(traceAnnotationRepository).queryList(eq(3), eq(1), eq(100));
        verify(traceAnnotationRepository).queryList(isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("score 筛选越界拒绝")
    public void testQueryScoreValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.queryList(9, 1, 20));
    }
}
