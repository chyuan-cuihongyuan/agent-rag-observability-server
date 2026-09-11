package cn.chyuan.ai.observability.domain.insight.service;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.chyuan.ai.observability.domain.insight.service.DataExportService.csvCell;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据导出服务单元测试（工单 0151 U5）— RFC 4180 转义、行拼装、长文本截断。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("数据导出服务测试")
class DataExportServiceTest {

    private final DataExportService service = new DataExportService();

    @Test
    @DisplayName("RFC 4180 转义 — 逗号/引号/换行包裹、内部引号翻倍、null 空串")
    public void testCsvCell() {
        assertEquals("", DataExportService.csvCell(null));
        assertEquals("plain", DataExportService.csvCell("plain"));
        assertEquals("\"a,b\"", DataExportService.csvCell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", DataExportService.csvCell("say \"hi\""));
        assertEquals("\"line1\nline2\"", DataExportService.csvCell("line1\nline2"));
    }

    @Test
    @DisplayName("chat_result 拼装 — 表头 + 数据行 + 答案长文本截断")
    public void testBuildChatResultsCsv() {
        ChatResultEntity c = ChatResultEntity.builder()
                .traceId("t-1").sessionId("s-1").tenantId("tenant").ownerUserId("alice")
                .agentId("200002").question("你好").answer("回答含,逗号和\"引号\"")
                .promptTokens(10).completionTokens(20).finalStatus("SUCCESS")
                .modelVersion("m-1").totalCostTimeMs(150).createTime("2026-09-11 10:00:00")
                .build();

        String csv = service.buildChatResultsCsv(List.of(c));

        assertTrue(csv.startsWith("traceId,sessionId,"));
        assertTrue(csv.contains(csvCell("回答含,逗号和\"引号\"")));
        assertTrue(csv.endsWith("\r\n"));
        assertEquals(2, csv.split("\r\n").length); // 表头 + 1 行
    }

    @Test
    @DisplayName("长文本截断 — 超过 1000 字符答案截断后不破坏 CSV 结构")
    public void testTruncateLongAnswer() {
        String longAnswer = "x".repeat(1500) + ",尾逗号";
        ChatResultEntity c = ChatResultEntity.builder()
                .traceId("t-2").question("q").answer(longAnswer).finalStatus("SUCCESS")
                .createTime("2026-09-11 10:00:00").build();

        String csv = service.buildChatResultsCsv(List.of(c));

        // 截断发生在转义前：1500 字符截到 1000，尾逗号被切掉 → 无引号包裹的干净单元
        assertFalse(csv.contains("尾逗号"));
        assertTrue(csv.length() < longAnswer.length());
    }

    @Test
    @DisplayName("eval_result 拼装 — 核心评分列齐")
    public void testBuildEvalResultsCsv() {
        EvalResultEntity r = EvalResultEntity.builder()
                .taskId("task-1").trialNo(1).traceId("t-9").queryText("q")
                .actualAnswer("a").overallScore(0.87).faithfulnessScore(0.9)
                .relevanceScore(0.8).hallucinationFlag(0).createTime("2026-09-11 10:00:00")
                .build();

        String csv = service.buildEvalResultsCsv(List.of(r));

        assertTrue(csv.startsWith("taskId,trialNo,"));
        assertTrue(csv.contains("task-1"));
        assertTrue(csv.contains("0.87"));
    }

    @Test
    @DisplayName("行数上限 — 默认 10000，可读出供控制器校验")
    public void testMaxRows() {
        assertTrue(service.maxRows() >= 1);
    }
}
