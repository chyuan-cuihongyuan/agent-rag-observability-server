package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.VoteResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自洽投票单测（工单 0327 AO5）：归一合并/并列首现/失败剔除/一致率。
 */
class SelfConsistencyVoterTest {

    @Test
    void 归一化合并票仓() {
        SelfConsistencyVoter voter = new SelfConsistencyVoter(Map.of());
        VoteResultVO result = voter.vote(List.of("Paris", " paris ", "PARIS", "London"));
        // 大小写+空白折叠后 paris 3 票胜出
        assertEquals("Paris", result.getAnswer(), "胜出答案取首现原始写法");
        assertEquals(0.75, result.getAgreementRate(), 1e-9);
        assertEquals(4, result.getValidSamples());
        assertEquals(0, result.getFailedSamples());
        assertEquals(2, result.getTally().size());
        assertEquals(3, result.getTally().get("paris").size());
    }

    @Test
    void 同义词归一与并列首现() {
        SelfConsistencyVoter voter = new SelfConsistencyVoter(Map.of("大模型", "llm"));
        VoteResultVO synonym = voter.vote(List.of("大模型", "LLM", "llm"));
        assertEquals("大模型", synonym.getAnswer());
        assertEquals(1.0, synonym.getAgreementRate(), 1e-9);
        // 并列：各 1 票取首现
        SelfConsistencyVoter plain = new SelfConsistencyVoter(Map.of());
        VoteResultVO tie = plain.vote(List.of("乙", "甲"));
        assertEquals("乙", tie.getAnswer(), "并列取首现序");
        assertEquals(0.5, tie.getAgreementRate(), 1e-9);
    }

    @Test
    void 失败采样剔除与全失败() {
        SelfConsistencyVoter voter = new SelfConsistencyVoter(Map.of());
        VoteResultVO partial = voter.vote(java.util.Arrays.asList("A", " ", null, "A"));
        assertEquals(2, partial.getValidSamples());
        assertEquals(2, partial.getFailedSamples());
        assertEquals(1.0, partial.getAgreementRate(), 1e-9);
        VoteResultVO allFailed = voter.vote(java.util.Arrays.asList("", "  "));
        assertNull(allFailed.getAnswer());
        assertEquals(0.0, allFailed.getAgreementRate());
        assertTrue(allFailed.getTally().isEmpty());
        VoteResultVO nullInput = voter.vote(null);
        assertNull(nullInput.getAnswer());
        assertEquals(0, nullInput.getValidSamples());
    }
}
