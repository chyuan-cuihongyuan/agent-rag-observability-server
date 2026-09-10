package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.valobj.EvalDatasetItem;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 三元组字段贯通测试（工单 0134 R2 验收）：
 * prompt/promptVersion/traceId 随 itemsJson 序列化/反序列化往返不丢失；
 * 既有 expected* 平铺字段（显式引用的期望行为组）同时保持兼容。
 */
@DisplayName("评测集条目 Task 三元组字段测试")
public class EvalDatasetItemTripleTest {

    @Test
    @DisplayName("三元组字段 — itemsJson 序列化往返不丢失")
    public void testTripleRoundTrip() {
        EvalDatasetItem item = EvalDatasetItem.builder()
                .query("查询订单状态")
                .standardAnswer("订单已发货")
                .standardChunks(List.of("chunk-1"))
                .prompt("帮我查一下订单 {orderId} 的物流状态")
                .promptVersion("order-query-v3")
                .traceId("trace-9f2a7b")
                .build();
        String json = JSON.toJSONString(item);
        assertTrue(json.contains("\"prompt\":\"帮我查一下订单 {orderId} 的物流状态\""), json);
        assertTrue(json.contains("order-query-v3"));
        assertTrue(json.contains("trace-9f2a7b"));

        EvalDatasetItem parsed = JSON.parseObject(json, EvalDatasetItem.class);
        assertEquals(item.getPrompt(), parsed.getPrompt());
        assertEquals(item.getPromptVersion(), parsed.getPromptVersion());
        assertEquals(item.getTraceId(), parsed.getTraceId());
        assertEquals(item.getQuery(), parsed.getQuery());
    }

    @Test
    @DisplayName("期望行为组 — 既有 expected* 平铺字段往返兼容")
    public void testExpectedBehaviorFieldsRoundTrip() {
        EvalDatasetItem item = EvalDatasetItem.builder()
                .query("q")
                .expectedTools(List.of("queryOrder", "pay"))
                .expectedToolParams(Map.of("orderId", "O-1"))
                .expectedIntentType("ORDER_QUERY")
                .expectedBranchType("TOOL_CALL")
                .expectedReasoningSteps("解析参数;调用工具;汇总回答")
                .build();
        EvalDatasetItem parsed = JSON.parseObject(JSON.toJSONString(item), EvalDatasetItem.class);
        assertEquals(item.getExpectedTools(), parsed.getExpectedTools());
        assertEquals(item.getExpectedToolParams().get("orderId"), parsed.getExpectedToolParams().get("orderId"));
        assertEquals("ORDER_QUERY", parsed.getExpectedIntentType());
        assertEquals("TOOL_CALL", parsed.getExpectedBranchType());
        assertEquals("解析参数;调用工具;汇总回答", parsed.getExpectedReasoningSteps());
    }

    @Test
    @DisplayName("存量兼容 — 旧格式条目（无三元组字段）解析不报错且三元组为 null")
    public void testLegacyJsonCompatible() {
        String legacy = "{\"query\":\"旧问题\",\"standardAnswer\":\"旧答案\",\"standardChunks\":[\"c\"]}";
        EvalDatasetItem parsed = JSON.parseObject(legacy, EvalDatasetItem.class);
        assertEquals("旧问题", parsed.getQuery());
        assertNull(parsed.getPrompt(), "旧数据无 prompt 为 null");
        assertNull(parsed.getPromptVersion());
        assertNull(parsed.getTraceId());
    }
}
