package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IPairwiseRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.PairwiseRecordEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Elo 评分引擎单元测试（工单 0171 X2）— 纯函数已知例、重放重算幂等、排序。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Elo 评分引擎测试")
class EloRatingServiceTest {

    @Mock
    private IPairwiseRecordRepository pairwiseRecordRepository;

    private EloRatingService serviceWithK(double k) {
        return new EloRatingService(pairwiseRecordRepository, k);
    }

    @Test
    @DisplayName("期望胜率 — 同分 0.5、差 400 分约 0.909")
    public void testExpectedScore() {
        assertEquals(0.5, EloRatingService.expectedScore(1200, 1200), 1e-9);
        assertEquals(0.5, EloRatingService.expectedScore(1500, 1500), 1e-9);
        assertEquals(0.909, EloRatingService.expectedScore(1400, 1000), 1e-3);
        assertEquals(0.091, EloRatingService.expectedScore(1000, 1400), 1e-3);
    }

    @Test
    @DisplayName("单场更新 — 胜加负减、K 因子缩放")
    public void testUpdatedRating() {
        // 强者胜：1200 vs 1200，K=32 → 1216 / 1184
        assertEquals(1216.0, EloRatingService.updatedRating(1200, 0.5, 1.0, 32), 1e-9);
        assertEquals(1184.0, EloRatingService.updatedRating(1200, 0.5, 0.0, 32), 1e-9);
        // 平局不变
        assertEquals(1200.0, EloRatingService.updatedRating(1200, 0.5, 0.5, 32), 1e-9);
    }

    @Test
    @DisplayName("对局重放 — 胜者升分降序排行，空历史返回空")
    public void testRecalculate() {
        when(pairwiseRecordRepository.queryList(isNull(), isNull(), anyInt())).thenReturn(List.of(
                record("model-v1", "model-v2", "A_WIN"),
                record("model-v1", "model-v2", "A_WIN")
        ));

        EloRatingService service = serviceWithK(32);
        Map<String, Double> ranking = service.recalculate();

        assertEquals(2, ranking.size());
        assertTrue(ranking.get("model-v1") > 1200);
        assertTrue(ranking.get("model-v2") < 1200);
        // 排序：胜者在前
        String first = ranking.keySet().iterator().next();
        assertEquals("model-v1", first);

        // 重算幂等：重放相同历史结果一致
        Map<String, Double> again = service.recalculate();
        assertEquals(ranking, again);
    }

    @Test
    @DisplayName("平局 — 同初值双方不变")
    public void testTieKeepsRatings() {
        when(pairwiseRecordRepository.queryList(isNull(), isNull(), anyInt())).thenReturn(List.of(
                record("m1", "m2", "TIE")
        ));

        Map<String, Double> ranking = serviceWithK(32).recalculate();

        assertEquals(1200.0, ranking.get("m1"), 1e-9);
        assertEquals(1200.0, ranking.get("m2"), 1e-9);
    }

    @Test
    @DisplayName("无对局的模型不出现")
    public void testNoPhantomModels() {
        when(pairwiseRecordRepository.queryList(isNull(), isNull(), anyInt())).thenReturn(List.of());

        assertTrue(serviceWithK(32).recalculate().isEmpty());
    }

    private PairwiseRecordEntity record(String taskA, String taskB, String outcome) {
        return PairwiseRecordEntity.builder().taskA(taskA).taskB(taskB).outcome(outcome).build();
    }
}
