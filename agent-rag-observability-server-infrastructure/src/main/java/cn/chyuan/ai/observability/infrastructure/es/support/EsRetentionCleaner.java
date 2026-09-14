package cn.chyuan.ai.observability.infrastructure.es.support;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ES trace 索引保留期清理（SELFLOOP3 loop-327，工单 0452/0453）
 *
 * <p>五类 trace 索引按月后缀滚动（如 chat_result_log-2026.09），本任务每日删除
 * 严格早于保留窗口（observability.es.retention-months，默认 12，0=禁用）的月度
 * 索引。解析失败的索引名一律跳过——不误删非日期索引；单索引失败不中断批次。</p>
 */
@Slf4j
@Component
public class EsRetentionCleaner {

    private static final List<String> INDEX_PREFIXES = List.of(
            "chat_result_log", "agent_decision_log", "rag_retrieval_log",
            "tool_call_log", "memory_recall_log");

    /** 索引名月后缀：前缀-YYYY.MM */
    private static final Pattern MONTH_SUFFIX = Pattern.compile("^([a-z_]+)-(\\d{4})\\.(\\d{2})$");

    private final int retentionMonths;

    @Resource
    private ElasticsearchClient esClient;

    public EsRetentionCleaner(
            @Value("${observability.es.retention-months:12}") int retentionMonths) {
        this.retentionMonths = retentionMonths;
    }

    @Scheduled(cron = "0 17 3 * * ?")
    public void cleanup() {
        if (retentionMonths <= 0) {
            return;
        }
        YearMonth cutoff = YearMonth.now().minusMonths(retentionMonths);
        for (String prefix : INDEX_PREFIXES) {
            try {
                Map<String, ?> indices = esClient.indices()
                        .get(GetIndexRequest.of(g -> g.index(prefix + "-*")))
                        .result();
                for (String indexName : indices.keySet()) {
                    if (isExpired(indexName, cutoff)) {
                        deleteQuietly(indexName);
                    }
                }
            } catch (Exception e) {
                log.warn("ES 保留期清理前缀处理失败: prefix={}, err={}", prefix, e.getMessage());
            }
        }
    }

    /** 判定纯函数：严格早于 cutoff 月才删（同月保留）；非日期后缀跳过 */
    static boolean isExpired(String indexName, YearMonth cutoff) {
        YearMonth month = monthOf(indexName);
        return month != null && month.isBefore(cutoff);
    }

    /** 解析索引名月后缀；非本清理域形态（前缀不匹配或后缀非日期）返回 null */
    static YearMonth monthOf(String indexName) {
        if (indexName == null) {
            return null;
        }
        Matcher m = MONTH_SUFFIX.matcher(indexName);
        if (!m.matches()) {
            return null;
        }
        if (!INDEX_PREFIXES.contains(m.group(1))) {
            return null;
        }
        try {
            return YearMonth.of(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }

    private void deleteQuietly(String indexName) {
        try {
            esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(indexName)));
            log.info("ES 保留期清理删除索引: {}", indexName);
        } catch (Exception e) {
            log.warn("ES 保留期清理删除失败: index={}, err={}", indexName, e.getMessage());
        }
    }
}
