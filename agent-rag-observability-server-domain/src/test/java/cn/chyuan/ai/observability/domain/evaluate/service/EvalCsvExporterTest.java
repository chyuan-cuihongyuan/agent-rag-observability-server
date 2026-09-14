package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 评测结果 CSV 导出（工单 0424/0425，SELFLOOP3 loop-313）
 */
class EvalCsvExporterTest {

    private EvalResultEntity result(String query, String answer) {
        EvalResultEntity e = new EvalResultEntity();
        e.setTraceId("t1");
        e.setQueryText(query);
        e.setStandardAnswer("标准");
        e.setActualAnswer(answer);
        e.setFaithfulnessScore(0.87);
        e.setOverallScore(0.9);
        e.setHallucinationFlag(0);
        return e;
    }

    @Test
    void emptyTaskExportsHeaderOnly() {
        IEvalResultRepository repo = mock(IEvalResultRepository.class);
        when(repo.queryByTaskId(eq("none"), anyInt(), anyInt())).thenReturn(List.of());

        String csv = new EvalCsvExporter(repo).export("none", 500);

        assertThat(csv.lines()).hasSize(1);
        assertThat(csv).startsWith("traceId,");
    }

    @Test
    void fieldsWithCommaQuoteNewlineAreEscaped() {
        assertThat(EvalCsvExporter.escape("普通")).isEqualTo("普通");
        assertThat(EvalCsvExporter.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(EvalCsvExporter.escape("他说\"hi\"")).isEqualTo("\"他说\"\"hi\"\"\"");
        assertThat(EvalCsvExporter.escape("两\n行")).isEqualTo("\"两\n行\"");
        assertThat(EvalCsvExporter.escape(null)).isEmpty();
    }

    @Test
    void rowsIncludeScoresAndRowLimitApplies() {
        IEvalResultRepository repo = mock(IEvalResultRepository.class);
        when(repo.queryByTaskId(eq("t1"), anyInt(), anyInt()))
                .thenReturn(List.of(result("q1", "a1"), result("q2", "a2"), result("q3", "a3")));

        String csv = new EvalCsvExporter(repo).export("t1", 2);

        List<String> lines = csv.lines().filter(l -> !l.isBlank()).toList();
        assertThat(lines).hasSize(3); // 表头 + 2 行（上限截断）
        assertThat(lines.get(1)).contains("q1").contains("0.87");
    }
}
