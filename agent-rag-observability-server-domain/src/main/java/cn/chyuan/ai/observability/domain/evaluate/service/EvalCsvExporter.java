package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 评测结果 CSV 导出 — judge 人审抽样（工单 0424/0425，SELFLOOP3 loop-313）
 * <p>
 * Phoenix human review 思想最小落地：导出 judge 打分明细供人工抽样复核。
 * RFC 4180 转义；UTF-8 BOM 由调用方写头（Excel 中文兼容）。
 */
@Component
public class EvalCsvExporter {

    private static final String HEADER = "traceId,问题,标准答案,实际答案,faithfulness,overallScore,幻觉标记";
    private static final int PAGE_SIZE = 200;

    private final IEvalResultRepository resultRepository;

    public EvalCsvExporter(IEvalResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    /** 导出任务结果为 CSV 正文（不含 BOM；空任务仅表头） */
    public String export(String taskId, int maxRows) {
        int limit = Math.max(1, Math.min(maxRows, 2000));
        StringBuilder sb = new StringBuilder(HEADER).append("\r\n");
        int fetched = 0;
        for (int page = 1; fetched < limit; page++) {
            List<EvalResultEntity> batch = resultRepository.queryByTaskId(taskId, page, PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            for (EvalResultEntity e : batch) {
                if (fetched >= limit) {
                    break;
                }
                sb.append(row(e)).append("\r\n");
                fetched++;
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }
        return sb.toString();
    }

    private String row(EvalResultEntity e) {
        List<String> cells = List.of(
                nz(e.getTraceId()),
                nz(e.getQueryText()),
                nz(e.getStandardAnswer()),
                nz(e.getActualAnswer()),
                e.getFaithfulnessScore() != null ? String.valueOf(e.getFaithfulnessScore()) : "",
                e.getOverallScore() != null ? String.valueOf(e.getOverallScore()) : "",
                e.getHallucinationFlag() != null ? String.valueOf(e.getHallucinationFlag()) : ""
        );
        List<String> escaped = new ArrayList<>(cells.size());
        for (String cell : cells) {
            escaped.add(escape(cell));
        }
        return String.join(",", escaped);
    }

    /** RFC 4180：含逗号/引号/换行的字段包引号，内部引号双写 */
    static String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needQuote = field.contains(",") || field.contains("\"")
                || field.contains("\n") || field.contains("\r");
        String replaced = field.replace("\"", "\"\"");
        return needQuote ? "\"" + replaced + "\"" : replaced;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
