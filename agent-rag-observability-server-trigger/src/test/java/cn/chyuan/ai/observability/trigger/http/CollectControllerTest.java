package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.collect.AgentDecisionDTO;
import cn.chyuan.ai.observability.api.dto.collect.ChatResultDTO;
import cn.chyuan.ai.observability.api.dto.collect.ObserveBatchDTO;
import cn.chyuan.ai.observability.api.dto.collect.RagRetrievalDTO;
import cn.chyuan.ai.observability.domain.observe.service.ObserveCollectService;
import cn.chyuan.ai.observability.infrastructure.redis.DashboardCacheService;
import cn.chyuan.ai.observability.types.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CollectController 单元测试
 * 测试数据采集相关的所有接口
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CollectController 数据采集接口测试")
class CollectControllerTest {

    @Mock
    private ObserveCollectService observeCollectService;

    @Mock
    private DashboardCacheService cacheService;

    @InjectMocks
    private CollectController collectController;

    @Test
    @DisplayName("采集 Agent 决策数据 - 成功")
    void collectAgentDecision_success() {
        // 准备测试数据
        AgentDecisionDTO dto = AgentDecisionDTO.builder()
                .traceId("trace-001")
                .sourceService("aggregation-support-agent")
                .tenantId("tenant-001")
                .ownerUserId("user-001")
                .sessionId("session-001")
                .agentId("agent-001")
                .userQuery("如何排查CPU使用率过高")
                .intentType("diagnose")
                .selectedToolList("prometheus_query,loki_search")
                .decisionReason("识别为运维告警场景")
                .branchType("REACT")
                .toolCallTimes(3)
                .toolRetryTimes(0)
                .agentStatus("SUCCESS")
                .costTimeMs(1500)
                .modelVersion("deepseek-v4-pro")
                .build();

        // 执行测试
        Response<String> response = collectController.collectAgentDecision(dto);

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("ok", response.getData());

        // 验证 service 被正确调用
        verify(observeCollectService, times(1)).collectAgentDecision(any());
        // 验证缓存计数器递增
        verify(cacheService, times(1)).increment("agent_decision");
    }

    @Test
    @DisplayName("采集 Agent 决策数据 - DTO字段全部为null时仍能正常处理")
    void collectAgentDecision_withNullFields() {
        // 准备一个所有字段都为null的DTO
        AgentDecisionDTO dto = new AgentDecisionDTO();

        // 执行测试
        Response<String> response = collectController.collectAgentDecision(dto);

        // 验证返回成功
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        verify(observeCollectService, times(1)).collectAgentDecision(any());
        verify(cacheService, times(1)).increment("agent_decision");
    }

    @Test
    @DisplayName("采集 RAG 检索数据 - 成功")
    void collectRagRetrieval_success() {
        // 准备测试数据
        RagRetrievalDTO dto = RagRetrievalDTO.builder()
                .traceId("trace-002")
                .sourceService("aggregation-support-agent")
                .tenantId("tenant-001")
                .ownerUserId("user-001")
                .sessionId("session-002")
                .agentId("agent-001")
                .queryText("如何配置Prometheus告警规则")
                .rewriteText("Prometheus告警配置方法")
                .retrievalTopk(10)
                .retrievalCount(5)
                .sourceDocs("[{\"docId\":\"doc1\",\"score\":0.95}]")
                .rerankScores("[{\"docId\":\"doc1\",\"score\":0.92}]")
                .emptyRetrieval(0)
                .retrievalCostMs(300)
                .ragStrategyVersion("v2.0")
                .build();

        // 执行测试
        Response<String> response = collectController.collectRagRetrieval(dto);

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("ok", response.getData());

        // 验证 service 被正确调用
        verify(observeCollectService, times(1)).collectRagRetrieval(any());
        // 验证缓存计数器递增
        verify(cacheService, times(1)).increment("rag_retrieval");
    }

    @Test
    @DisplayName("采集 RAG 检索数据 - 空检索结果场景")
    void collectRagRetrieval_emptyRetrieval() {
        // 准备一个空检索结果的数据
        RagRetrievalDTO dto = RagRetrievalDTO.builder()
                .traceId("trace-003")
                .emptyRetrieval(1)
                .retrievalCount(0)
                .build();

        // 执行测试
        Response<String> response = collectController.collectRagRetrieval(dto);

        // 验证即使空检索也能正常采集
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        verify(observeCollectService, times(1)).collectRagRetrieval(any());
    }

    @Test
    @DisplayName("采集对话结果数据 - 成功")
    void collectChatResult_success() {
        // 准备测试数据
        ChatResultDTO dto = ChatResultDTO.builder()
                .traceId("trace-004")
                .sourceService("aggregation-support-agent")
                .tenantId("tenant-001")
                .ownerUserId("user-001")
                .sessionId("session-004")
                .agentId("agent-001")
                .question("如何排查CPU使用率过高？")
                .answer("建议按以下步骤排查：1. 查看top命令...")
                .promptTokens(500)
                .completionTokens(200)
                .totalCostTimeMs(2000)
                .finalStatus("SUCCESS")
                .modelVersion("deepseek-v4-pro")
                .build();

        // 执行测试
        Response<String> response = collectController.collectChatResult(dto);

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("ok", response.getData());

        // 验证 service 被正确调用
        verify(observeCollectService, times(1)).collectChatResult(any());
        // 验证缓存计数器递增
        verify(cacheService, times(1)).increment("chat_result");
    }

    @Test
    @DisplayName("采集对话结果数据 - DTO字段全部为null时仍能正常处理")
    void collectChatResult_withNullFields() {
        // 准备一个所有字段都为null的DTO
        ChatResultDTO dto = new ChatResultDTO();

        // 执行测试
        Response<String> response = collectController.collectChatResult(dto);

        // 验证返回成功
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        verify(observeCollectService, times(1)).collectChatResult(any());
        verify(cacheService, times(1)).increment("chat_result");
    }

    @Test
    @DisplayName("批量采集 - 三种数据同时提交成功")
    void collectBatch_allThreeTypes() {
        // 准备包含全部三种数据的批量DTO
        AgentDecisionDTO agentDTO = AgentDecisionDTO.builder()
                .traceId("trace-batch-001").sourceService("svc")
                .sessionId("session-batch").agentId("agent-001")
                .userQuery("查询").intentType("diagnose")
                .build();

        RagRetrievalDTO ragDTO = RagRetrievalDTO.builder()
                .traceId("trace-batch-002").sourceService("svc")
                .sessionId("session-batch").agentId("agent-001")
                .queryText("查询文本").retrievalCount(3)
                .build();

        ChatResultDTO chatDTO = ChatResultDTO.builder()
                .traceId("trace-batch-003").sourceService("svc")
                .sessionId("session-batch").agentId("agent-001")
                .question("问题").answer("答案")
                .build();

        ObserveBatchDTO batchDTO = ObserveBatchDTO.builder()
                .agentDecision(agentDTO)
                .ragRetrieval(ragDTO)
                .chatResult(chatDTO)
                .build();

        // 执行测试
        Response<String> response = collectController.collectBatch(batchDTO);

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("ok", response.getData());

        // 验证三种 service 方法各调用一次
        verify(observeCollectService, times(1)).collectAgentDecision(any());
        verify(observeCollectService, times(1)).collectRagRetrieval(any());
        verify(observeCollectService, times(1)).collectChatResult(any());

        // 验证三种缓存计数器各递增一次
        verify(cacheService, times(1)).increment("agent_decision");
        verify(cacheService, times(1)).increment("rag_retrieval");
        verify(cacheService, times(1)).increment("chat_result");
    }

    @Test
    @DisplayName("批量采集 - 仅包含 Agent 决策数据")
    void collectBatch_onlyAgentDecision() {
        // 仅包含 Agent 决策数据
        AgentDecisionDTO agentDTO = AgentDecisionDTO.builder()
                .traceId("trace-batch-010").sessionId("session-010")
                .build();

        ObserveBatchDTO batchDTO = ObserveBatchDTO.builder()
                .agentDecision(agentDTO)
                .ragRetrieval(null)
                .chatResult(null)
                .build();

        // 执行测试
        Response<String> response = collectController.collectBatch(batchDTO);

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());

        // 验证仅调用 Agent 决策的采集方法
        verify(observeCollectService, times(1)).collectAgentDecision(any());
        verify(observeCollectService, never()).collectRagRetrieval(any());
        verify(observeCollectService, never()).collectChatResult(any());

        verify(cacheService, times(1)).increment("agent_decision");
        verify(cacheService, never()).increment("rag_retrieval");
        verify(cacheService, never()).increment("chat_result");
    }

    @Test
    @DisplayName("批量采集 - 仅包含 RAG 检索数据")
    void collectBatch_onlyRagRetrieval() {
        RagRetrievalDTO ragDTO = RagRetrievalDTO.builder()
                .traceId("trace-batch-011").queryText("查询")
                .build();

        ObserveBatchDTO batchDTO = ObserveBatchDTO.builder()
                .agentDecision(null)
                .ragRetrieval(ragDTO)
                .chatResult(null)
                .build();

        // 执行测试
        Response<String> response = collectController.collectBatch(batchDTO);

        // 验证返回结果
        assertNotNull(response);
        assertEquals("0000", response.getCode());

        // 验证仅调用 RAG 检索的采集方法
        verify(observeCollectService, never()).collectAgentDecision(any());
        verify(observeCollectService, times(1)).collectRagRetrieval(any());
        verify(observeCollectService, never()).collectChatResult(any());

        verify(cacheService, never()).increment("agent_decision");
        verify(cacheService, times(1)).increment("rag_retrieval");
        verify(cacheService, never()).increment("chat_result");
    }

    @Test
    @DisplayName("批量采集 - 所有字段都为null时跳过所有采集")
    void collectBatch_allNullFields() {
        // 全部为null
        ObserveBatchDTO batchDTO = new ObserveBatchDTO();

        // 执行测试
        Response<String> response = collectController.collectBatch(batchDTO);

        // 验证返回成功（空批量操作也是合法的）
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("ok", response.getData());

        // 验证没有调用任何采集方法
        verify(observeCollectService, never()).collectAgentDecision(any());
        verify(observeCollectService, never()).collectRagRetrieval(any());
        verify(observeCollectService, never()).collectChatResult(any());
        verifyNoInteractions(cacheService);
    }
}
