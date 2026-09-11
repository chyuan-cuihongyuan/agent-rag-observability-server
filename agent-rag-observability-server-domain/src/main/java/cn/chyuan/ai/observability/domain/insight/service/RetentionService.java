package cn.chyuan.ai.observability.domain.insight.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志保留清理服务（工单 0152 U6）— 按批次删除超期日志。
 * <ul>
 *   <li>表白名单：chat_result_log / agent_decision_log / rag_retrieval_log（三张最大日志表；
 *       评测与注解资产表不在清理范围）</li>
 *   <li>批次编排：单表循环删到不足一批或达单表轮次上限；单表失败不阻塞其余表（来源隔离）</li>
 *   <li>dry-run：只统计不删除</li>
 * </ul>
 */
@Slf4j
@Service
public class RetentionService {

    /** 可清理表白名单（防 SQL 注入：表名不走外部输入） */
    public static final List<String> PURGEABLE_TABLES =
            List.of("chat_result_log", "agent_decision_log", "rag_retrieval_log");

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 单表单轮最大批次数（防一次性长事务：batchSize×轮次为单表单轮上限） */
    private static final int MAX_ROUNDS_PER_TABLE = 50;

    private final RetentionExecutor retentionExecutor;

    @Value("${retention.batch-size:500}")
    private int batchSize;

    public RetentionService(RetentionExecutor retentionExecutor) {
        this.retentionExecutor = retentionExecutor;
    }

    /** 测试专用：显式批大小 */
    RetentionService(RetentionExecutor retentionExecutor, int batchSize) {
        this.retentionExecutor = retentionExecutor;
        this.batchSize = batchSize;
    }

    /** 执行器桥（mapper 在 infrastructure，通过接口注入保持 domain 零技术依赖） */
    public interface RetentionExecutor {
        int purgeBatch(String table, String beforeTime, int batchSize);

        long countPurge(String table, String beforeTime);
    }

    /**
     * 清理 beforeTime 之前的日志。
     *
     * @return 每表删除行数汇总
     */
    public Map<String, Long> purge(LocalDateTime before) {
        Map<String, Long> result = new LinkedHashMap<>();
        String beforeTime = FMT.format(before);
        for (String table : PURGEABLE_TABLES) {
            long deleted = 0;
            try {
                for (int round = 0; round < MAX_ROUNDS_PER_TABLE; round++) {
                    int n = retentionExecutor.purgeBatch(table, beforeTime, batchSize);
                    deleted += n;
                    if (n < batchSize) {
                        break;
                    }
                }
            } catch (Exception e) {
                // 单表失败隔离：记录后继续下一张表
                log.warn("保留清理单表失败（隔离）: table={}, err={}", table, e.getMessage());
            }
            result.put(table, deleted);
        }
        log.info("保留清理完成: before={}, deleted={}", beforeTime, result);
        return result;
    }

    /** dry-run：只统计各表超期条数，不删除 */
    public Map<String, Long> dryRun(LocalDateTime before) {
        Map<String, Long> result = new LinkedHashMap<>();
        String beforeTime = FMT.format(before);
        for (String table : PURGEABLE_TABLES) {
            try {
                result.put(table, retentionExecutor.countPurge(table, beforeTime));
            } catch (Exception e) {
                log.warn("保留清理统计失败: table={}, err={}", table, e.getMessage());
            }
        }
        return result;
    }
}
