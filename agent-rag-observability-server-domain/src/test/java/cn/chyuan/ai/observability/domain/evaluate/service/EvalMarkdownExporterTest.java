package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvalMarkdownExporter 单测（SELFLOOP4 loop-432，工单 0656/0657）。
 * 内联 fake 仓储驱动分页与空任务路径，不拉 Spring。
 */
class EvalMarkdownExporterTest {

    /** 内联 fake：按页切分固定数据 */
    private static class FakeRepo implements IEvalResultRepository {
        private final List<EvalResultEntity> data;

        FakeRepo(List<EvalResultEntity> data) {
            this.data = data;
        }

        @Override
        public List<EvalResultEntity> queryByTaskId(String taskId, int page, int size) {
            int from = (page - 1) * size;
            if (from >= data.size()) {
                return List.of();
            }
            return new ArrayList<>(data.subList(from, Math.min(from + size, data.size())));
        }

        @Override
        public void save(EvalResultEntity entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void batchSave(List<EvalResultEntity> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByTaskId(String taskId) {
            return data.size();
        }
    }

    private static EvalResultEntity result(String traceId, String query, Double overall, Integer hallucination) {
        EvalResultEntity e = new EvalResultEntity();
        e.setTraceId(traceId);
        e.setQueryText(query);
        e.setOverallScore(overall);
        e.setHallucinationFlag(hallucination);
        return e;
    }

    @Test
    @DisplayName("正常导出：标题 + 计数 + 表头 + 行数据")
    void exportsHeaderAndRows() {
        EvalMarkdownExporter exporter = new EvalMarkdownExporter(
                new FakeRepo(List.of(result("t-1", "什么是RAG", 0.87, 0))));
        String md = exporter.export("task-9", 100);

        assertThat(md).startsWith("# 评测报告 — 任务 task-9");
        assertThat(md).contains("共 1 条结果");
        assertThat(md).contains(EvalMarkdownExporter.HEADER);
        assertThat(md).contains("t-1|什么是RAG");
        assertThat(md).contains("0.87");
    }

    @Test
    @DisplayName("空任务：仅标题 + 空表提示，无表格结构")
    void emptyTaskRendersPlaceholder() {
        EvalMarkdownExporter exporter = new EvalMarkdownExporter(new FakeRepo(List.of()));
        String md = exporter.export("task-empty", 100);
        assertThat(md).contains("暂无评测结果");
        assertThat(md).doesNotContain(EvalMarkdownExporter.HEADER);
    }

    @Test
    @DisplayName("转义：问题文本含管道符/换行不破表")
    void cellContentEscaped() {
        EvalResultEntity tricky = result("t-2", "a|b\nc", 0.5, null);
        String md = new EvalMarkdownExporter(new FakeRepo(List.of(tricky))).export("task-x", 10);
        assertThat(md).contains("a\\|b<br>c");
    }

    @Test
    @DisplayName("maxRows 上限钳位到 2000，null 分值渲染为 —")
    void limitClampedAndNullScoreDash() {
        List<EvalResultEntity> big = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            big.add(result("t-" + i, "q" + i, null, null));
        }
        String md = new EvalMarkdownExporter(new FakeRepo(big)).export("task-big", 5000);
        assertThat(md).contains("共 30 条结果");
        assertThat(md).contains("|||—|—|—");
    }
}
