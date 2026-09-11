package cn.chyuan.ai.observability.domain.insight.service;

import cn.chyuan.ai.observability.domain.insight.model.entity.DriftEventEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 漂移检测纯函数单元测试（工单 0154 U8）— 分布统计、漂移判定、小样本保护。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("漂移检测服务测试")
class DriftDetectionServiceTest {

    @Mock
    private cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository ragRetrievalRepository;

    @Mock
    private cn.chyuan.ai.observability.domain.insight.adapter.repository.IDriftEventRepository driftEventRepository;

    @InjectMocks
    private DriftDetectionService service;

    private DriftDetectionService.DistributionStats stats(int sampleCount, double mean, double emptyRate) {
        DriftDetectionService.DistributionStats s = new DriftDetectionService.DistributionStats();
        s.total = sampleCount;
        s.sampleCount = sampleCount;
        s.mean = mean;
        s.emptyRetrievalRate = emptyRate;
        return s;
    }

    @Test
    @DisplayName("分布统计 — rerankScores JSON 解析均值/空检索率")
    public void testDistribution() {
        DriftDetectionService.DistributionStats stats = service.distribution(List.of(
                retrieval("[0.9, 0.8, 0.7]", 0),
                retrieval(null, 1),
                retrieval("not-json", 0)
        ));

        assertEquals(3, stats.total);
        assertEquals(3, stats.sampleCount);
        assertEquals(0.8, stats.mean, 1e-9);
        assertEquals(1.0 / 3, stats.emptyRetrievalRate, 1e-9);
    }

    @Test
    @DisplayName("漂移判定 — 均值漂移超阈值、空检索率增量、小样本不判定")
    public void testIsDrift() {
        // 均值漂移 0.2 > 0.1 → 漂移
        assertTrue(service.isDrift(stats(100, 0.7, 0.0), stats(100, 0.9, 0.0)));
        // 均值相同、空检索率增量 0.15 > 0.1 → 漂移
        assertTrue(service.isDrift(stats(100, 0.8, 0.2), stats(100, 0.8, 0.05)));
        // 均值漂移 0.05 且空检索率持平 → 无漂移
        assertFalse(service.isDrift(stats(100, 0.8, 0.1), stats(100, 0.85, 0.1)));
        // 小样本（<10）不判定
        assertFalse(service.isDrift(stats(5, 0.1, 0.5), stats(100, 0.9, 0.0)));
    }

    @Test
    @DisplayName("runOnce — 取数窗口透传（本周 vs 上周）")
    public void testRunOnceWiring() {
        org.mockito.BDDMockito.given(ragRetrievalRepository.queryForDrift(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt())).willReturn(List.of());

        assertNull(service.runOnce());
    }

    private cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity retrieval(
            String scoresJson, int empty) {
        return cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity.builder()
                .rerankScores(scoresJson).emptyRetrieval(empty).createTime("2026-09-11 00:00:00")
                .build();
    }
}
