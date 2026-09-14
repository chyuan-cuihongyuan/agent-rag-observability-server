package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评测数据集版本快照与漂移检测（SELFLOOP3 loop-334，工单 0466/0467）
 */
class EvaluateServiceDatasetSnapshotTest {

    @Test
    void contentHashIsStableAndNullSafe() {
        assertThat(EvaluateService.contentHash(null)).isEqualTo(EvaluateService.contentHash(""));
        assertThat(EvaluateService.contentHash("[{\"q\":\"你好\"}]"))
                .hasSize(64)
                .isEqualTo(EvaluateService.contentHash("[{\"q\":\"你好\"}]"))
                .isNotEqualTo(EvaluateService.contentHash("[{\"q\":\"变更\"}]"));
    }

    @Test
    void createTaskSnapshotsDatasetContentHash() {
        IEvalTaskRepository taskRepo = mock(IEvalTaskRepository.class);
        IEvalDatasetRepository datasetRepo = mock(IEvalDatasetRepository.class);
        EvalDatasetEntity dataset = new EvalDatasetEntity();
        dataset.setItemsJson("[{\"q\":\"样本\"}]");
        when(datasetRepo.queryByDatasetId(anyString())).thenReturn(dataset);

        EvaluateService service = new EvaluateService(
                taskRepo, mock(IEvalResultRepository.class), datasetRepo);

        service.createTask(EvalTaskEntity.builder().taskName("t").evalType("ANSWER_QUALITY").datasetId("ds1").build());

        ArgumentCaptor<EvalTaskEntity> captor = ArgumentCaptor.forClass(EvalTaskEntity.class);
        verify(taskRepo).save(captor.capture());
        assertThat(captor.getValue().getDatasetContentHash())
                .isEqualTo(EvaluateService.contentHash("[{\"q\":\"样本\"}]"));
    }
}
