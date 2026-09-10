package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.evaluate.EvalDatasetDTO;
import cn.chyuan.ai.observability.api.dto.evaluate.EvalResultDTO;
import cn.chyuan.ai.observability.api.dto.evaluate.EvalTaskDTO;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.service.EvaluateService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 评测控制器单元测试
 * <p>
 * 测试场景：
 * 1. 创建数据集
 * 2. 查询数据集列表
 * 3. 创建评测任务
 * 4. 查询任务列表
 * 5. 查询任务详情
 * 6. 运行评测任务
 * 7. 保存评测结果
 * 8. 查询评测结果
 * 9. 版本对比
 * 10. 无效参数校验
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("评测控制器测试")
public class EvaluateControllerTest {

    @Mock
    private EvaluateService evaluateService;

    @InjectMocks
    private EvaluateController controller;

    @Test
    @DisplayName("创建数据集 — 成功返回 datasetId")
    public void testCreateDataset_Success() {
        // 准备
        EvalDatasetDTO dto = new EvalDatasetDTO();
        dto.setDatasetId("ds-001");
        dto.setDatasetName("测试数据集");
        dto.setDescription("用于测试的数据集");
        dto.setItemCount(10);
        dto.setItemsJson("[{\"q\":\"问题1\",\"a\":\"答案1\"}]");

        // 执行
        Response<String> result = controller.createDataset(dto);

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertEquals("ds-001", result.getData(), "应返回 datasetId");
        verify(evaluateService).saveDataset(any(EvalDatasetEntity.class));
    }

    @Test
    @DisplayName("查询数据集列表 — 成功返回分页列表")
    public void testListDatasets_Success() {
        // 准备
        when(evaluateService.queryDatasetList(1, 20)).thenReturn(Collections.emptyList());

        // 执行
        Response<Map<String, Object>> result = controller.listDatasets(1, 20);

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertNotNull(result.getData(), "数据不应为 null");
        assertTrue(result.getData().containsKey("list"), "应包含 list 字段");
    }

    @Test
    @DisplayName("查询数据集列表 — 无效分页参数返回错误")
    public void testListDatasets_InvalidPage() {
        // 执行
        Response<Map<String, Object>> result = controller.listDatasets(-1, 20);

        // 验证
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode(), "响应码应为参数非法");
    }

    @Test
    @DisplayName("创建评测任务 — 成功返回 taskId")
    public void testCreateTask_Success() {
        // 准备
        EvalTaskDTO dto = new EvalTaskDTO();
        dto.setTaskName("RAG 检索评测");
        dto.setEvalType("RAG_RETRIEVAL");
        dto.setDatasetId("ds-001");
        dto.setModelVersion("deepseek-v4");
        when(evaluateService.createTask(any(EvalTaskEntity.class))).thenReturn("task-001");

        // 执行
        Response<String> result = controller.createTask(dto);

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertEquals("task-001", result.getData(), "应返回 taskId");
    }

    @Test
    @DisplayName("查询任务列表 — 成功返回任务列表")
    public void testListTasks_Success() {
        // 准备
        EvalTaskEntity entity = new EvalTaskEntity();
        entity.setTaskId("task-001");
        entity.setTaskName("测试任务");
        entity.setEvalType("RAG_RETRIEVAL");
        entity.setStatus("PENDING");
        when(evaluateService.queryTaskList(1, 20)).thenReturn(List.of(entity));

        // 执行
        Response<Map<String, Object>> result = controller.listTasks(1, 20);

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertNotNull(result.getData().get("list"), "应包含任务列表");
    }

    @Test
    @DisplayName("查询任务详情 — 成功返回任务信息")
    public void testQueryTask_Success() {
        // 准备
        EvalTaskEntity entity = new EvalTaskEntity();
        entity.setTaskId("task-001");
        entity.setTaskName("测试任务");
        entity.setEvalType("RAG_RETRIEVAL");
        entity.setStatus("COMPLETED");
        entity.setAvgOverallScore(0.85);
        when(evaluateService.queryTask("task-001")).thenReturn(entity);

        // 执行
        Response<EvalTaskDTO> result = controller.queryTask("task-001");

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertNotNull(result.getData(), "数据不应为 null");
        assertEquals("task-001", result.getData().getTaskId(), "taskId 应正确");
        assertEquals("COMPLETED", result.getData().getStatus(), "状态应为 COMPLETED");
    }

    @Test
    @DisplayName("查询任务详情 — 任务不存在返回 null")
    public void testQueryTask_NotFound() {
        // 准备
        when(evaluateService.queryTask("non-exist")).thenReturn(null);

        // 执行
        Response<EvalTaskDTO> result = controller.queryTask("non-exist");

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertNull(result.getData(), "不存在的任务应返回 null");
    }

    @Test
    @DisplayName("运行评测任务 — 成功触发运行")
    public void testRunTask_Success() {
        // 执行
        Response<String> result = controller.runTask("task-001");

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertEquals("task-001", result.getData(), "应返回 taskId");
        verify(evaluateService).runTask("task-001");
    }

    @Test
    @DisplayName("保存评测结果 — 成功保存")
    public void testSaveResult_Success() {
        // 准备
        EvalResultDTO dto = new EvalResultDTO();
        dto.setTaskId("task-001");
        dto.setTraceId("trace-001");
        dto.setQueryText("测试问题");
        dto.setStandardAnswer("标准答案");
        dto.setActualAnswer("实际答案");
        dto.setRecallScore(0.9);
        dto.setPrecisionScore(0.85);
        dto.setF1Score(0.87);
        dto.setOverallScore(0.88);

        // 执行
        Response<String> result = controller.saveResult(dto);

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertEquals("ok", result.getData(), "应返回 ok");
        verify(evaluateService).saveResult(any(EvalResultEntity.class));
    }

    @Test
    @DisplayName("查询评测结果 — 成功返回结果列表")
    public void testQueryResults_Success() {
        // 准备（trial 缺省查全部 trial，工单 0135 R3 口径）
        when(evaluateService.queryResultsByTaskId("task-001", null, 1, 20))
                .thenReturn(Collections.emptyList());
        when(evaluateService.countResultsByTaskId("task-001")).thenReturn(0L);

        // 执行
        Response<Map<String, Object>> result = controller.queryResults("task-001", 1, 20, null);

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertTrue(result.getData().containsKey("list"), "应包含 list");
        assertTrue(result.getData().containsKey("total"), "应包含 total");
    }

    @Test
    @DisplayName("查询评测结果 — trial 过滤透传（工单 0135 R3）")
    public void testQueryResults_TrialFilter() {
        when(evaluateService.queryResultsByTaskId("task-001", 2, 1, 20))
                .thenReturn(Collections.emptyList());
        when(evaluateService.countResultsByTaskId("task-001")).thenReturn(3L);

        Response<Map<String, Object>> result = controller.queryResults("task-001", 1, 20, 2);

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        verify(evaluateService).queryResultsByTaskId("task-001", 2, 1, 20);
    }

    @Test
    @DisplayName("查询评测结果 — 非法 trial（<1）返回参数错误")
    public void testQueryResults_IllegalTrial() {
        Response<Map<String, Object>> result = controller.queryResults("task-001", 1, 20, 0);

        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode());
        verify(evaluateService, never()).queryResultsByTaskId(anyString(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("版本对比 — 成功返回对比数据")
    public void testCompareResults_Success() {
        // 准备
        when(evaluateService.compareResults("task-001", "task-002"))
                .thenReturn(List.of(Map.of("metric", "recall", "task1", 0.9, "task2", 0.85)));

        // 执行
        Response<List<Map<String, Object>>> result = controller.compareResults("task-001", "task-002");

        // 验证
        assertEquals(ResponseCode.SUCCESS, result.getCode(), "响应码应为 0000");
        assertNotNull(result.getData(), "对比数据不应为 null");
        assertEquals(1, result.getData().size(), "应有 1 条对比记录");
    }

    @Test
    @DisplayName("版本对比 — 无效 taskId 返回参数错误")
    public void testCompareResults_InvalidTaskId() {
        // 执行
        Response<List<Map<String, Object>>> result = controller.compareResults("", "task-002");

        // 验证
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode(), "响应码应为参数非法");
    }

    // ===== 工单 0134 R2：三池筛选 / 版本化 / 冻结端点 =====

    @Test
    @DisplayName("创建数据集 — 更新已冻结版本被拒绝（冻结不变式）")
    public void testCreateDataset_FrozenRejected() {
        // 准备：带 datasetId 更新 → 服务层抛冻结校验异常
        EvalDatasetDTO dto = new EvalDatasetDTO();
        dto.setDatasetId("ds-frozen");
        dto.setDatasetName("黄金集");
        dto.setItemsJson("[{modified:true}]");
        doThrow(new IllegalArgumentException("数据集版本已冻结，禁止修改条目: ds-frozen"))
                .when(evaluateService).saveDataset(any(EvalDatasetEntity.class));

        // 执行
        Response<String> result = controller.createDataset(dto);

        // 验证
        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode(), "冻结版本修改应返回参数非法");
        assertTrue(result.getInfo().contains("已冻结"), "错误信息应说明冻结: " + result.getInfo());
    }

    @Test
    @DisplayName("创建数据集 — 新建透传版本/池/来源/冻结字段")
    public void testCreateDataset_NewFieldsPassed() {
        EvalDatasetDTO dto = new EvalDatasetDTO();
        dto.setDatasetId("ds-new");
        dto.setDatasetName("新黄金集");
        dto.setItemsJson("[]");
        dto.setVersion(2);
        dto.setPool("golden");
        dto.setSource("trace");
        dto.setFrozen(false);

        Response<String> result = controller.createDataset(dto);

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        ArgumentCaptor<EvalDatasetEntity> captor = ArgumentCaptor.forClass(EvalDatasetEntity.class);
        verify(evaluateService).saveDataset(captor.capture());
        assertEquals(2, captor.getValue().getVersion(), "version 应透传");
        assertEquals("golden", captor.getValue().getPool(), "pool 应透传");
        assertEquals("trace", captor.getValue().getSource(), "source 应透传");
    }

    @Test
    @DisplayName("三池筛选 — 成功返回池内列表")
    public void testListByPool_Success() {
        when(evaluateService.queryDatasetByPool("golden", 1, 20))
                .thenReturn(List.of(EvalDatasetEntity.builder()
                        .datasetId("ds-1").datasetName("黄金集").version(1)
                        .pool("golden").source("manual").frozen(false).build()));

        Response<Map<String, Object>> result = controller.listDatasetsByPool("golden", 1, 20);

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        assertTrue(result.getData().containsKey("list"));
    }

    @Test
    @DisplayName("三池筛选 — 非法池名返回参数错误")
    public void testListByPool_IllegalPool() {
        when(evaluateService.queryDatasetByPool("diamond", 1, 20))
                .thenThrow(new IllegalArgumentException("非法样本池: diamond"));

        Response<Map<String, Object>> result = controller.listDatasetsByPool("diamond", 1, 20);

        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode(), "非法池名应返回参数非法");
    }

    @Test
    @DisplayName("版本列表 — 按名称返回全部版本")
    public void testListVersions_Success() {
        when(evaluateService.queryDatasetVersions("ds-1")).thenReturn(List.of(
                EvalDatasetEntity.builder().datasetId("ds-2").datasetName("黄金集").version(2).build(),
                EvalDatasetEntity.builder().datasetId("ds-1").datasetName("黄金集").version(1).build()));

        Response<Map<String, Object>> result = controller.listDatasetVersions("ds-1");

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        assertEquals(2, ((List<?>) result.getData().get("list")).size());
    }

    @Test
    @DisplayName("快照复制 — 返回新 datasetId 与递增版本")
    public void testCopyVersion_Success() {
        when(evaluateService.copyDatasetVersion("ds-1")).thenReturn(EvalDatasetEntity.builder()
                .datasetId("ds-new-777").datasetName("黄金集").version(3)
                .pool("golden").frozen(false).build());

        Response<Map<String, Object>> result = controller.copyDatasetVersion("ds-1");

        assertEquals(ResponseCode.SUCCESS, result.getCode());
        assertEquals("ds-new-777", result.getData().get("datasetId"));
        assertEquals(3, result.getData().get("version"));
        assertEquals(Boolean.FALSE, result.getData().get("frozen"));
    }

    @Test
    @DisplayName("快照复制 — 源不存在返回参数错误")
    public void testCopyVersion_NotFound() {
        when(evaluateService.copyDatasetVersion("ds-none"))
                .thenThrow(new IllegalArgumentException("数据集不存在: ds-none"));

        Response<Map<String, Object>> result = controller.copyDatasetVersion("ds-none");

        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode());
    }

    @Test
    @DisplayName("冻结/解冻 — 成功委托服务并返回 ok")
    public void testFreezeAndUnfreeze_Success() {
        Response<String> freeze = controller.freezeDataset("ds-1");
        assertEquals(ResponseCode.SUCCESS, freeze.getCode());
        verify(evaluateService).freezeDataset("ds-1", true);

        Response<String> unfreeze = controller.unfreezeDataset("ds-1");
        assertEquals(ResponseCode.SUCCESS, unfreeze.getCode());
        verify(evaluateService).freezeDataset("ds-1", false);
    }

    @Test
    @DisplayName("冻结 — 不存在的数据集返回参数错误")
    public void testFreeze_NotFound() {
        doThrow(new IllegalArgumentException("数据集不存在: ds-none"))
                .when(evaluateService).freezeDataset("ds-none", true);

        Response<String> result = controller.freezeDataset("ds-none");

        assertEquals(ResponseCode.ILLEGAL_PARAMETER, result.getCode());
    }
}
