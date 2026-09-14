package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.PublishSuggestionVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发布建议单测（工单 0330 AO8）：状态机/导出 JSON/重复与非法转移。
 */
class PromptPublishAdvisorTest {

    @Test
    void 建议状态机合法转移() {
        PromptPublishAdvisor advisor = new PromptPublishAdvisor();
        PublishSuggestionVO proposed = advisor.propose("sug-1", "exp-1", "新提示", "基线提示",
                0.15, "ds-001", "提升显著", 1000);
        assertEquals(PublishSuggestionVO.PROPOSED, proposed.getStatus());
        assertEquals(1000, proposed.getProposedAtMs());
        PublishSuggestionVO accepted = advisor.decide("sug-1", PublishSuggestionVO.ACCEPTED, 2000);
        assertEquals(PublishSuggestionVO.ACCEPTED, accepted.getStatus());
        assertEquals(2000, accepted.getDecidedAtMs());
        // 终态不可再转
        assertThrows(IllegalStateException.class, () ->
                advisor.decide("sug-1", PublishSuggestionVO.REJECTED, 3000));
        assertEquals(1, advisor.list().size());
    }

    @Test
    void 拒绝路径与重复ID拒绝() {
        PromptPublishAdvisor advisor = new PromptPublishAdvisor();
        advisor.propose("sug-2", "exp-2", "P", "B", -0.01, "ds", "回落", 1);
        advisor.decide("sug-2", PublishSuggestionVO.REJECTED, 2);
        assertEquals(PublishSuggestionVO.REJECTED, advisor.list().get(0).getStatus());
        assertThrows(IllegalArgumentException.class, () ->
                advisor.propose("sug-2", "exp-2", "P", "B", 0, "ds", "", 3));
        assertThrows(IllegalArgumentException.class, () -> advisor.decide("ghost", "ACCEPTED", 4));
    }

    @Test
    void 导出JSON确定性含基线对比字段() {
        PromptPublishAdvisor advisor = new PromptPublishAdvisor();
        PublishSuggestionVO suggestion = advisor.propose("sug-3", "exp-3",
                "带\"引号\"提示", "基线", 0.2, "ds-003", "", 42);
        String json = advisor.export(suggestion);
        // 重放一致
        assertEquals(json, advisor.export(advisor.list().get(0)));
        assertTrue(json.contains("\"lift\":0.2"));
        assertTrue(json.contains("\"baselinePrompt\":\"基线\""));
        assertTrue(json.contains("\"status\":\"PROPOSED\""));
        assertTrue(json.contains("带\\\"引号\\\"提示"), "引号应转义");
        assertTrue(json.contains("\"decidedAtMs\":0"));
        assertThrows(IllegalArgumentException.class, () -> advisor.export(null));
    }
}
