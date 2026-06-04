package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.collect.AgentDecisionDTO;
import cn.chyuan.ai.observability.api.dto.collect.ChatResultDTO;
import cn.chyuan.ai.observability.api.dto.collect.ObserveBatchDTO;
import cn.chyuan.ai.observability.api.dto.collect.RagRetrievalDTO;
import cn.chyuan.ai.observability.domain.observe.service.ObserveCollectService;
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
 * 注：缓存计数已下沉到 ObserveCollectService，Controller 不再直接调用缓存
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CollectController 数据采集接口测试")
class CollectControllerTest {

    @Mock
    private ObserveCollectService observeCollectService;

    @InjectMocks
    private CollectController collectController;

    @Test
    @DisplayName("采集 Agent 决策数据 - 成功")
    void collectAgentDecision_success() {
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

        Response<String> response = collectController.collectAgentDecision(dto);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("ok", response.getData());
        verify(observeCollectService, times(1)).collectAgentDecision(any());
    }

    @Test
    @DisplayName("采集 Agent 决策数据 - DTO字段全部为null时仍能正常处理")
    void collectAgentDecision_withNullFields() {
        AgentDecisionDTO dto = new AgentDecisionDTO();

        Response<String> response = collectController.collectAgentDecision(dto);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        verify(observeCollectService, times(1)).collectAgentDecision(any());
    }

    @Test
    @DisplayName("采集 RAG 检索数据 - 成功")
    void collectRagRetrieval_success() {
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

        Response<String> response = collectController.collectRagRetrieval(dto);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("ok", response.getData());
        verify(observeCollectService, times(1)).collectRagRetrieval(any());
    }

    @Test
    @DisplayName("采集 RAG 检索数据 - 空检索结果场景")
    void collectRagRetrieval_emptyRetrieval() {
        RagRetrievalDTO dto = RagRetrievalDTO.builder()
                .traceId("trace-003")
                .emptyRetrieval(1)
                .retrievalCount(0)
                .build();

        Response<String> response = collectController.collectRagRetrieval(dto);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        verify(observeCollectService, times(1)).collectRagRetrieval(any());
    }

    @Test
    @DisplayName("采集对话结果数据 - 成功")
    void collectChatResult_success() {
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

        Response<String> response = collectController.collectChatResult(dto);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("ok", response.getData());
        verify(observeCollectService, times(1)).collectChatResult(any());
    }

    @Test
    @DisplayName("采集对话结果数据 - DTO字段全部为null时仍能正常处理")
    void collectChatResult_withNullFields() {
        ChatResultDTO dto = new ChatResultDTO();

        Response<String> response = collectController.collectChatResult(dto);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        verify(observeCollectService, times(1)).collectChatResult(any());
    }

    @Test
    @DisplayName("批量采集 - 三种数据同时提交成功")
    void collectBatch_allThreeTypes() {
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

        Response<String> response = collectController.collectBatch(batchDTO);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("ok", response.getData());
        verify(observeCollectService, times(1)).collectAgentDecision(any());
        verify(observeCollectService, times(1)).collectRagRetrieval(any());
        verify(observeCollectService, times(1)).collectChatResult(any());
    }

    @Test
    @DisplayName("批量采集 - 仅包含 Agent 决策数据")
    void collectBatch_onlyAgentDecision() {
        AgentDecisionDTO agentDTO = AgentDecisionDTO.builder()
                .traceId("trace-batch-010").sessionId("session-010")
                .build();

        ObserveBatchDTO batchDTO = ObserveBatchDTO.builder()
                .agentDecision(agentDTO)
                .ragRetrieval(null)
                .chatResult(null)
                .build();

        Response<String> response = collectController.collectBatch(batchDTO);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        verify(observeCollectService, times(1)).collectAgentDecision(any());
        verify(observeCollectService, never()).collectRagRetrieval(any());
        verify(observeCollectService, never()).collectChatResult(any());
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

        Response<String> response = collectController.collectBatch(batchDTO);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        verify(observeCollectService, never()).collectAgentDecision(any());
        verify(observeCollectService, times(1)).collectRagRetrieval(any());
        verify(observeCollectService, never()).collectChatResult(any());
    }

    @Test
    @DisplayName("批量采集 - 所有字段都为null时跳过所有采集")
    void collectBatch_allNullFields() {
        ObserveBatchDTO batchDTO = new ObserveBatchDTO();

        Response<String> response = collectController.collectBatch(batchDTO);

        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("ok", response.getData());
        verify(observeCollectService, never()).collectAgentDecision(any());
        verify(observeCollectService, never()).collectRagRetrieval(any());
        verify(observeCollectService, never()).collectChatResult(any());
    }
}
