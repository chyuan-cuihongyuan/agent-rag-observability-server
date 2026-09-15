package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 评测结果 Markdown 导出（SELFLOOP4 loop-432，工单 0656/0657；承接 SELFLOOP3 O05）。
 * <p>
 * 与 {@link EvalCsvExporter} 同构：分页拉取（200/批，上限 2000 行）、构造注入、
 * 纯函数行格式化。面向人读场景（评审/归档/工单附文），输出 GFM 表格；
 * 单元格转义管道符与换行，防表格结构被内容破坏。
 */
@Component
public class EvalMarkdownExporter {

    private static final int PAGE_SIZE = 200;

    private final IEvalResultRepository resultRepository;

    public EvalMarkdownExporter(IEvalResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    /** 导出任务结果为 markdown 报告（空任务仅标题与空表提示） */
    public String export(String taskId, int maxRows) {
        int limit = Math.max(1, Math.min(maxRows, 2000));
        StringBuilder sb = new StringBuilder("# 评测报告 — 任务 ")
                .append(taskId == null || taskId.isBlank() ? "(未命名)" : taskId)
                .append("\n\n");
        int fetched = 0;
        StringBuilder table = new StringBuilder();
        for (int page = 1; fetched < limit; page++) {
            List<EvalResultEntity> batch = resultRepository.queryByTaskId(taskId, page, PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            for (EvalResultEntity e : batch) {
                if (fetched == 0) {
                    table.append(HEADER).append('\n')
                         .append(":---|:---|:---|:---|---:|---:|---:\n");
                }
                table.append(row(e)).append('\n');
                fetched++;
                if (fetched >= limit) {
                    break;
                }
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }
        if (fetched == 0) {
            sb.append("> 该任务暂无评测结果。\n");
            return sb.toString();
        }
        sb.append("共 ").append(fetched).append(" 条结果\n\n").append(table);
        return sb.toString();
    }

    private String row(EvalResultEntity e) {
        List<String> cells = List.of(
                nz(e.getTraceId()),
                nz(e.getQueryText()),
                nz(e.getStandardAnswer()),
                nz(e.getActualAnswer()),
                num(e.getFaithfulnessScore()),
                num(e.getOverallScore()),
                e.getHallucinationFlag() != null ? String.valueOf(e.getHallucinationFlag()) : "—"
        );
        List<String> escaped = new java.util.ArrayList<>(cells.size());
        for (String cell : cells) {
            escaped.add(escapeMd(cell));
        }
        return String.join("|", escaped);
    }

    static final String HEADER = "traceId|问题|标准答案|实际答案|faithfulness|overallScore|幻觉标记";

    /** GFM 表格转义：管道符转义防破表，换行替换为 <br> 保持单行 */
    static String escapeMd(String field) {
        if (field == null) {
            return "";
        }
        return field.replace("|", "\\|").replace("\r\n", "<br>").replace("\n", "<br>").replace("\r", "<br>");
    }

    private static String num(Double v) {
        return v != null ? String.valueOf(v) : "—";
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
