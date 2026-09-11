package cn.chyuan.ai.observability.domain.insight.service;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据导出服务（工单 0151 U5）— chat_result / eval_result 的 CSV 导出拼装。
 * 转义 RFC 4180（含逗号/引号/换行整字段包裹，内部引号翻倍），对齐网关 0112/0090 先例；
 * 行数上限配置（export.max-rows 默认 10000），超限由控制器拒绝。
 */
@Slf4j
@Service
public class DataExportService {

    @Value("${export.max-rows:10000}")
    private int maxRows;

    /** 行数上限（控制器校验用） */
    public int maxRows() {
        return Math.max(maxRows, 1);
    }

    /** chat_result 导出（含表头；\r\n 行分隔） */
    public String buildChatResultsCsv(List<ChatResultEntity> chats) {
        StringBuilder csv = new StringBuilder();
        csv.append("traceId,sessionId,tenantId,ownerUserId,agentId,question,answer,promptTokens,completionTokens,finalStatus,modelVersion,totalCostTimeMs,createTime\r\n");
        for (ChatResultEntity c : chats) {
            csv.append(csvCell(c.getTraceId())).append(',')
                    .append(csvCell(c.getSessionId())).append(',')
                    .append(csvCell(c.getTenantId())).append(',')
                    .append(csvCell(c.getOwnerUserId())).append(',')
                    .append(csvCell(c.getAgentId())).append(',')
                    .append(csvCell(truncate(c.getQuestion()))).append(',')
                    .append(csvCell(truncate(c.getAnswer()))).append(',')
                    .append(csvCell(String.valueOf(c.getPromptTokens()))).append(',')
                    .append(csvCell(String.valueOf(c.getCompletionTokens()))).append(',')
                    .append(csvCell(c.getFinalStatus())).append(',')
                    .append(csvCell(c.getModelVersion())).append(',')
                    .append(csvCell(String.valueOf(c.getTotalCostTimeMs()))).append(',')
                    .append(csvCell(c.getCreateTime())).append("\r\n");
        }
        return csv.toString();
    }

    /** eval_result 导出（明细列取核心评分；evalDetail JSON 截断防巨行） */
    public String buildEvalResultsCsv(List<EvalResultEntity> results) {
        StringBuilder csv = new StringBuilder();
        csv.append("taskId,trialNo,traceId,queryText,actualAnswer,overallScore,faithfulnessScore,relevanceScore,hallucinationFlag,createTime\r\n");
        for (EvalResultEntity r : results) {
            csv.append(csvCell(r.getTaskId())).append(',')
                    .append(csvCell(String.valueOf(r.getTrialNo()))).append(',')
                    .append(csvCell(r.getTraceId())).append(',')
                    .append(csvCell(truncate(r.getQueryText()))).append(',')
                    .append(csvCell(truncate(r.getActualAnswer()))).append(',')
                    .append(csvCell(String.valueOf(r.getOverallScore()))).append(',')
                    .append(csvCell(String.valueOf(r.getFaithfulnessScore()))).append(',')
                    .append(csvCell(String.valueOf(r.getRelevanceScore()))).append(',')
                    .append(csvCell(String.valueOf(r.getHallucinationFlag()))).append(',')
                    .append(csvCell(r.getCreateTime())).append("\r\n");
        }
        return csv.toString();
    }

    /** CSV 单元格转义（RFC 4180：含逗号/引号/换行整字段包裹，内部引号翻倍） */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** 长文本截断（TEXT 答案/问题导出截 1000 字符，防巨行） */
    private String truncate(String v) {
        if (v == null) {
            return null;
        }
        return v.length() <= 1000 ? v : v.substring(0, 1000);
    }
}
