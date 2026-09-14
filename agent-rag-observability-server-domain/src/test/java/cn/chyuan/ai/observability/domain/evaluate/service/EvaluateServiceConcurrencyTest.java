package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评测任务并发闸门 — 饱和拒绝 / 许可归还（工单 0404/0405，SELFLOOP3 loop-303）
 */
class EvaluateServiceConcurrencyTest {

    private EvaluateService serviceWith(IEvalTaskRepository taskRepo,
                                        EvalExecutionService execution,
                                        EvalConcurrencyGuard guard) {
        EvaluateService service = new EvaluateService(
                taskRepo,
                mock(IEvalResultRepository.class),
                mock(IEvalDatasetRepository.class));
        ReflectionTestUtils.setField(service, "evalExecutionService", execution);
        ReflectionTestUtils.setField(service, "evalConcurrencyGuard", guard);
        return service;
    }

    private IEvalTaskRepository taskRepoReturningTask() {
        IEvalTaskRepository taskRepo = mock(IEvalTaskRepository.class);
        when(taskRepo.queryByTaskId("t1")).thenReturn(new EvalTaskEntity());
        return taskRepo;
    }

    @Test
    void saturatedGuardRejectsTaskWithoutExecution() {
        IEvalTaskRepository taskRepo = taskRepoReturningTask();
        EvalExecutionService execution = mock(EvalExecutionService.class);
        EvalConcurrencyGuard guard = new EvalConcurrencyGuard(1);
        assertThat(guard.tryAcquire()).isTrue(); // 占满唯一许可

        EvaluateService service = serviceWith(taskRepo, execution, guard);
        service.runTask("t1");

        verify(taskRepo).updateStatus("t1", "FAILED");
        verify(execution, never()).execute(any());
        guard.release(); // 复位
    }

    @Test
    void permitIsReturnedAfterSuccessSoNextTaskRuns() {
        IEvalTaskRepository taskRepo = taskRepoReturningTask();
        EvalExecutionService execution = mock(EvalExecutionService.class);
        EvaluateService service = serviceWith(taskRepo, execution, new EvalConcurrencyGuard(1));

        service.runTask("t1");
        service.runTask("t1"); // 第二次仍能执行 → 第一次已归还许可

        verify(execution, times(2)).execute(any());
        verify(taskRepo, times(2)).updateStatus("t1", "RUNNING");
    }

    @Test
    void permitIsReturnedAfterFailure() {
        IEvalTaskRepository taskRepo = taskRepoReturningTask();
        EvalExecutionService execution = mock(EvalExecutionService.class);
        doThrow(new RuntimeException("judge down")).when(execution).execute(any());
        EvaluateService service = serviceWith(taskRepo, execution, new EvalConcurrencyGuard(1));

        service.runTask("t1");
        service.runTask("t1"); // 若首次未归还，第二次将被拒绝而不执行

        verify(execution, times(2)).execute(any());
        verify(taskRepo, times(2)).updateStatus("t1", "FAILED");
    }
}
