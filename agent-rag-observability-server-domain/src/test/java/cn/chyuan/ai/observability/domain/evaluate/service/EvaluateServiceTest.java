package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluateServiceTest {

    @Mock
    private IEvalTaskRepository evalTaskRepository;

    @Mock
    private IEvalResultRepository evalResultRepository;

    @Mock
    private IEvalDatasetRepository evalDatasetRepository;

    @Mock
    private EvalExecutionService evalExecutionService;

    @InjectMocks
    private EvaluateService evaluateService;

    @Test
    void testCreateTask_Success() {
        // Given
        EvalTaskEntity entity = EvalTaskEntity.builder()
                .datasetId("dataset-1")
                .name("Test Task")
                .build();

        // When
        String taskId = evaluateService.createTask(entity);

        // Then
        assertNotNull(taskId);
        assertEquals(16, taskId.length());
        assertEquals("PENDING", entity.getStatus());
        assertNotNull(entity.getCreateTime());
        assertNotNull(entity.getUpdateTime());
        verify(evalTaskRepository).save(entity);
    }

    @Test
    void testQueryTask_Success() {
        // Given
        String taskId = "task-123";
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId(taskId)
                .name("Test Task")
                .build();

        when(evalTaskRepository.queryByTaskId(eq(taskId))).thenReturn(task);

        // When
        EvalTaskEntity result = evaluateService.queryTask(taskId);

        // Then
        assertNotNull(result);
        assertEquals(taskId, result.getTaskId());
        verify(evalTaskRepository).queryByTaskId(eq(taskId));
    }

    @Test
    void testQueryTask_NotFound() {
        // Given
        String taskId = "non-existent";
        when(evalTaskRepository.queryByTaskId(eq(taskId))).thenReturn(null);

        // When
        EvalTaskEntity result = evaluateService.queryTask(taskId);

        // Then
        assertNull(result);
    }

    @Test
    void testQueryTaskList_Success() {
        // Given
        int page = 1;
        int size = 10;
        List<EvalTaskEntity> tasks = Arrays.asList(
                EvalTaskEntity.builder().taskId("task1").build(),
                EvalTaskEntity.builder().taskId("task2").build()
        );

        when(evalTaskRepository.queryList(eq(page), eq(size))).thenReturn(tasks);

        // When
        List<EvalTaskEntity> result = evaluateService.queryTaskList(page, size);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(evalTaskRepository).queryList(eq(page), eq(size));
    }

    @Test
    void testUpdateTaskStatus_Success() {
        // Given
        String taskId = "task-123";
        String status = "RUNNING";

        // When
        evaluateService.updateTaskStatus(taskId, status);

        // Then
        verify(evalTaskRepository).updateStatus(eq(taskId), eq(status));
    }

    @Test
    void testSaveResult_Success() {
        // Given
        EvalResultEntity entity = EvalResultEntity.builder()
                .taskId("task-123")
                .overallScore(0.85)
                .build();

        // When
        evaluateService.saveResult(entity);

        // Then
        assertNotNull(entity.getCreateTime());
        verify(evalResultRepository).save(entity);
    }

    @Test
    void testBatchSaveResults_Success() {
        // Given
        List<EvalResultEntity> entities = Arrays.asList(
                EvalResultEntity.builder().taskId("task-1").build(),
                EvalResultEntity.builder().taskId("task-2").build()
        );

        // When
        evaluateService.batchSaveResults(entities);

        // Then
        entities.forEach(e -> assertNotNull(e.getCreateTime()));
        verify(evalResultRepository).batchSave(entities);
    }

    @Test
    void testQueryResultsByTaskId_Success() {
        // Given
        String taskId = "task-123";
        int page = 1;
        int size = 10;
        List<EvalResultEntity> results = Arrays.asList(
                EvalResultEntity.builder().taskId(taskId).overallScore(0.9).build()
        );

        when(evalResultRepository.queryByTaskId(eq(taskId), eq(page), eq(size))).thenReturn(results);

        // When
        List<EvalResultEntity> result = evaluateService.queryResultsByTaskId(taskId, page, size);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(evalResultRepository).queryByTaskId(eq(taskId), eq(page), eq(size));
    }

    @Test
    void testCountResultsByTaskId_Success() {
        // Given
        String taskId = "task-123";
        when(evalResultRepository.countByTaskId(eq(taskId))).thenReturn(5L);

        // When
        long count = evaluateService.countResultsByTaskId(taskId);

        // Then
        assertEquals(5L, count);
        verify(evalResultRepository).countByTaskId(eq(taskId));
    }

    @Test
    void testSaveDataset_Success() {
        // Given
        EvalDatasetEntity entity = EvalDatasetEntity.builder()
                .datasetId("dataset-1")
                .name("Test Dataset")
                .build();

        // When
        evaluateService.saveDataset(entity);

        // Then
        verify(evalDatasetRepository).save(entity);
    }

    @Test
    void testQueryDataset_Success() {
        // Given
        String datasetId = "dataset-123";
        EvalDatasetEntity dataset = EvalDatasetEntity.builder()
                .datasetId(datasetId)
                .name("Test Dataset")
                .build();

        when(evalDatasetRepository.queryByDatasetId(eq(datasetId))).thenReturn(dataset);

        // When
        EvalDatasetEntity result = evaluateService.queryDataset(datasetId);

        // Then
        assertNotNull(result);
        assertEquals(datasetId, result.getDatasetId());
        verify(evalDatasetRepository).queryByDatasetId(eq(datasetId));
    }

    @Test
    void testQueryDatasetList_Success() {
        // Given
        int page = 1;
        int size = 10;
        List<EvalDatasetEntity> datasets = Arrays.asList(
                EvalDatasetEntity.builder().datasetId("dataset1").build()
        );

        when(evalDatasetRepository.queryList(eq(page), eq(size))).thenReturn(datasets);

        // When
        List<EvalDatasetEntity> result = evaluateService.queryDatasetList(page, size);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(evalDatasetRepository).queryList(eq(page), eq(size));
    }

    @Test
    void testRunTask_Success() {
        // Given
        String taskId = "task-123";
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId(taskId)
                .status("PENDING")
                .build();

        when(evalTaskRepository.queryByTaskId(eq(taskId))).thenReturn(task);

        // When
        evaluateService.runTask(taskId);

        // Then
        verify(evalTaskRepository).updateStatus(eq(taskId), eq("RUNNING"));
        verify(evalExecutionService).execute(task);
    }

    @Test
    void testRunTask_TaskNotFound() {
        // Given
        String taskId = "non-existent";
        when(evalTaskRepository.queryByTaskId(eq(taskId))).thenReturn(null);

        // When
        evaluateService.runTask(taskId);

        // Then
        verify(evalTaskRepository, never()).updateStatus(any(), any());
        verify(evalExecutionService, never()).execute(any());
    }

    @Test
    void testRunTask_ExecutionFailed() {
        // Given
        String taskId = "task-123";
        EvalTaskEntity task = EvalTaskEntity.builder()
                .taskId(taskId)
                .status("PENDING")
                .build();

        when(evalTaskRepository.queryByTaskId(eq(taskId))).thenReturn(task);
        doThrow(new RuntimeException("Execution error")).when(evalExecutionService).execute(task);

        // When
        evaluateService.runTask(taskId);

        // Then
        verify(evalTaskRepository).updateStatus(eq(taskId), eq("RUNNING"));
        verify(evalTaskRepository).updateStatus(eq(taskId), eq("FAILED"));
    }

    @Test
    void testCompareResults_Success() {
        // Given
        String task1 = "task-1";
        String task2 = "task-2";

        List<EvalResultEntity> results1 = Arrays.asList(
                EvalResultEntity.builder().overallScore(0.8).recallScore(0.7).faithfulnessScore(0.9).build(),
                EvalResultEntity.builder().overallScore(0.9).recallScore(0.8).faithfulnessScore(0.85).build()
        );

        List<EvalResultEntity> results2 = Arrays.asList(
                EvalResultEntity.builder().overallScore(0.75).recallScore(0.65).faithfulnessScore(0.8).build()
        );

        when(evalResultRepository.queryByTaskId(eq(task1), eq(1), eq(500))).thenReturn(results1);
        when(evalResultRepository.queryByTaskId(eq(task1), eq(2), eq(500))).thenReturn(Arrays.asList());
        when(evalResultRepository.queryByTaskId(eq(task2), eq(1), eq(500))).thenReturn(results2);
        when(evalResultRepository.queryByTaskId(eq(task2), eq(2), eq(500))).thenReturn(Arrays.asList());

        // When
        List<Map<String, Object>> result = evaluateService.compareResults(task1, task2);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        
        Map<String, Object> task1Result = result.get(0);
        assertEquals(task1, task1Result.get("taskId"));
        assertEquals(0.85, (Double) task1Result.get("avgOverallScore"), 0.01);
        assertEquals(0.75, (Double) task1Result.get("avgRecallScore"), 0.01);
        assertEquals(0.875, (Double) task1Result.get("avgFaithfulnessScore"), 0.01);
        assertEquals(2, task1Result.get("count"));

        Map<String, Object> task2Result = result.get(1);
        assertEquals(task2, task2Result.get("taskId"));
        assertEquals(0.75, (Double) task2Result.get("avgOverallScore"), 0.01);
        assertEquals(1, task2Result.get("count"));
    }

    @Test
    void testCompareResults_EmptyResults() {
        // Given
        String task1 = "task-1";
        String task2 = "task-2";

        when(evalResultRepository.queryByTaskId(eq(task1), eq(1), eq(500))).thenReturn(Arrays.asList());
        when(evalResultRepository.queryByTaskId(eq(task2), eq(1), eq(500))).thenReturn(Arrays.asList());

        // When
        List<Map<String, Object>> result = evaluateService.compareResults(task1, task2);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        
        Map<String, Object> task1Result = result.get(0);
        assertEquals(0.0, task1Result.get("avgOverallScore"));
        assertEquals(0, task1Result.get("count"));
    }
}
