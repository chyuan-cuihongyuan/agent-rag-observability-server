package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EvalResultValidator 分值域校验单测（SELFLOOP4 loop-425，工单 0648/0649）。
 */
class EvalResultValidatorTest {

    private EvalResultEntity base() {
        return EvalResultEntity.builder()
                .taskId("t-1").traceId("trace-1")
                .recallScore(0.8).f1Score(0.75).faithfulnessScore(1.0)
                .overallScore(0.9).hallucinationFlag(0)
                .evalDetail("{}")
                .build();
    }

    @Test
    @DisplayName("合法结果零违规")
    void validResultPasses() {
        assertTrue(EvalResultValidator.violations(base()).isEmpty());
    }

    @Test
    @DisplayName("越界与非法值逐一报违规（overall>1 / 分值<0 / flag=2）")
    void outOfRangeReported() {
        EvalResultEntity r = base();
        r.setOverallScore(1.5);
        r.setFaithfulnessScore(-0.1);
        r.setHallucinationFlag(2);
        List<String> problems = EvalResultValidator.violations(r);
        assertEquals(3, problems.size());
        assertTrue(problems.stream().anyMatch(p -> p.contains("overallScore")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("faithfulnessScore")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("hallucinationFlag")));
    }

    @Test
    @DisplayName("overall 缺失报违规；verdict 缺席的 null 分值合法")
    void nullOverallReportedButNullScoresLegal() {
        EvalResultEntity r = base();
        r.setOverallScore(null);
        assertNotEquals(0, EvalResultValidator.violations(r).size());

        EvalResultEntity noVerdict = base(); // context 系列与工具系列全 null = RAG_RETRIEVAL 常态
        assertTrue(EvalResultValidator.violations(noVerdict).isEmpty());
    }
}
