package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.adapter.repository.InMemoryOptimExperimentRepository;
import cn.chyuan.ai.observability.domain.evaluate.promptopt.adapter.repository.OptimExperimentRepository;
import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.OptimExperimentVO;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 优化实验留痕单测（工单 0329 AO7）：落库/查询/曲线对比/DDL 守卫另测。
 */
class OptimExperimentRecordTest {

    private OptimExperimentVO experiment(String id, String name, String curve, long createdAtMs) {
        return OptimExperimentVO.builder()
                .experimentId(id)
                .name(name)
                .signatureFingerprint("sig-" + id)
                .datasetFingerprint("ds-001")
                .candidatesJson("[\"候选A\",\"候选B\"]")
                .scoreCurveJson(curve)
                .winner("候选B")
                .status("DONE")
                .createdAtMs(createdAtMs)
                .updatedAtMs(createdAtMs)
                .build();
    }

    @Test
    void 落库与查询() {
        InMemoryOptimExperimentRepository repository = new InMemoryOptimExperimentRepository();
        repository.save(experiment("exp-1", "基线实验", "[0.5,0.6]", 1000));
        repository.save(experiment("exp-2", "优选实验", "[0.6,0.85]", 2000));
        assertEquals(2, repository.listAll().size());
        Optional<OptimExperimentVO> found = repository.findById("exp-1");
        assertTrue(found.isPresent());
        assertEquals("sig-exp-1", found.get().getSignatureFingerprint());
        // 创建时间排序
        assertEquals("exp-1", repository.listByCreatedAsc().get(0).getExperimentId());
        // 覆盖保存（同 ID 更新）
        repository.save(experiment("exp-1", "改名", "[0.7]", 3000));
        assertEquals("改名", repository.findById("exp-1").orElseThrow().getName());
        assertEquals(2, repository.listAll().size());
    }

    @Test
    void 曲线对比与提升幅度() {
        OptimExperimentRepository repository = new InMemoryOptimExperimentRepository();
        repository.save(experiment("base", "基线", "[0.4,0.6]", 1));
        repository.save(experiment("opt", "优选", "[0.5,0.9]", 2));
        OptimExperimentRepository.CurveCompareVO compare =
                repository.compare("base", "opt").orElseThrow();
        assertEquals(0.6, compare.leftBest(), 1e-9);
        assertEquals(0.9, compare.rightBest(), 1e-9);
        assertEquals(0.3, compare.lift(), 1e-9);
        // 缺失实验空对比
        assertTrue(repository.compare("base", "ghost").isEmpty());
        // 坏曲线 JSON 按 0
        assertEquals(0.0, InMemoryOptimExperimentRepository.bestOf("not-json"), 1e-9);
        assertEquals(0.0, InMemoryOptimExperimentRepository.bestOf(null), 1e-9);
    }

    @Test
    void 非法保存拒绝() {
        InMemoryOptimExperimentRepository repository = new InMemoryOptimExperimentRepository();
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        assertThrows(IllegalArgumentException.class, () -> repository.save(
                experiment(" ", "无名", "[]", 1)));
    }
}
