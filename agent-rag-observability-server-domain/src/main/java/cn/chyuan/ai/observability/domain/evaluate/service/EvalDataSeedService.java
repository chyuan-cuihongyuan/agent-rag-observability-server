package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 评测数据填充服务 — 生成模拟的评测任务 + 评测结果，用于主页「RAG 质量概览」面板展示和开发调试。
 * <p>
 * 与 observe 模块的 DataSeedService 对齐：不走 LLM（种子要快），指标按「优/中/差」三档合理分布生成，
 * 让质量面板的绿/黄/红配色都有体现，避免全 1.0（失真）或纯随机（不合理）。
 */
@Slf4j
@Service
public class EvalDataSeedService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IEvalTaskRepository evalTaskRepository;
    private final IEvalResultRepository evalResultRepository;

    public EvalDataSeedService(IEvalTaskRepository evalTaskRepository,
                               IEvalResultRepository evalResultRepository) {
        this.evalTaskRepository = evalTaskRepository;
        this.evalResultRepository = evalResultRepository;
    }

    /** 用户查询池（与 observe DataSeedService 保持一致，保证可读性） */
    private static final String[] QUERIES = {
            "查询最近1小时CPU使用率超过80%的服务器",
            "分析今天凌晨的告警风暴根因",
            "检索微服务 order-service 的部署架构文档",
            "帮我查看 Redis 连接池的监控指标",
            "分析过去24小时的应用日志错误趋势",
            "检查数据库慢查询 TOP 10",
            "搜索 Kafka 消费延迟的排查方案",
            "查询网关层 5xx 错误的分布情况",
            "检索 JVM GC 调优的最佳实践",
            "查询 MySQL 主从同步延迟状态",
    };

    /** 答案池（与 observe ANSWERS 对齐） */
    private static final String[] ANSWERS = {
            "根据监控数据分析，最近1小时有3台服务器CPU使用率超过80%，分别是：10.0.1.15（92%）、10.0.1.23（87%）、10.0.2.8（83%）。建议检查是否有异常进程占用资源。",
            "告警风暴的根因分析如下：凌晨2:15由于网络抖动导致 Prometheus 误触发200+条告警，实际需要关注的是数据库连接池耗尽问题，建议调整告警聚合规则。",
            "order-service 的部署架构文档已检索到，该服务采用 Spring Cloud 微服务架构，包含网关层、服务层和数据层三部分，详见知识库文档 KB-2024-0089。",
            "Redis 连接池监控指标：当前活跃连接数 48/200（24%），等待队列长度 0，平均获取连接耗时 2ms，连接池状态健康。",
            "过去24小时的日志错误趋势：总计 1,247 条错误，主要集中在 14:00-16:00 时段，NullPointerException 占 43%，TimeoutException 占 28%。",
            "慢查询 TOP 10 已生成，最慢查询耗时 12.3s（SELECT * FROM order_detail WHERE ...），建议添加组合索引 (tenant_id, create_time)。",
            "Kafka 消费延迟排查方案：建议检查消费者 group 的 Lag 指标，当前 consumer-group-1 的 Lag 为 15,230 条，可通过增加分区或消费者实例缓解。",
            "网关层 5xx 错误分布：过去1小时共 89 次，其中 503 占 65%（上游服务不可用），502 占 25%（网关超时），500 占 10%（程序异常）。",
    };

    /** 评测类型池 */
    private static final String[] EVAL_TYPES = {"RAG_RETRIEVAL", "ANSWER_QUALITY", "CONTEXT_QUALITY"};

    /**
     * 生成评测种子数据。
     *
     * @param taskCount    生成几个评测任务（建议 3，覆盖三种类型）
     * @param itemsPerTask 每个任务多少条评测结果
     * @return 生成的评测结果总数
     */
    public int seedEvalData(int taskCount, int itemsPerTask) {
        int totalResults = 0;
        LocalDateTime now = LocalDateTime.now();

        for (int t = 0; t < taskCount; t++) {
            String evalType = EVAL_TYPES[t % EVAL_TYPES.length];
            String taskId = "seed-eval-" + UUID.randomUUID().toString().substring(0, 12);
            String taskName = String.format("示例评测-%s-%s", evalTypeLabel(evalType), t + 1);
            String datasetId = "seed-dataset-" + (t + 1);
            String nowStr = now.format(FMT);

            // 1. 写评测任务（直接置 COMPLETED，带 avg 分）
            EvalTaskEntity task = EvalTaskEntity.builder()
                    .taskId(taskId)
                    .taskName(taskName)
                    .evalType(evalType)
                    .datasetId(datasetId)
                    .status("COMPLETED")
                    .totalCount(itemsPerTask)
                    .completedCount(itemsPerTask)
                    .modelVersion("deepseek-v4-pro")
                    .ragStrategyVersion("v2.1")
                    .avgOverallScore(0.0) // 占位，下面算完再 update
                    .createTime(nowStr)
                    .updateTime(nowStr)
                    .build();
            evalTaskRepository.save(task);

            // 2. 生成评测结果（按优/中/差三档分布）
            List<EvalResultEntity> results = new ArrayList<>();
            double sumOverall = 0.0;
            for (int i = 0; i < itemsPerTask; i++) {
                EvalResultEntity r = buildResult(taskId, evalType, i, nowStr);
                results.add(r);
                sumOverall += r.getOverallScore() != null ? r.getOverallScore() : 0.0;
            }
            evalResultRepository.batchSave(results);

            // 3. 回写任务平均分
            double avg = sumOverall / itemsPerTask;
            evalTaskRepository.updateProgress(taskId, itemsPerTask, round(avg));

            totalResults += itemsPerTask;
        }

        log.info("评测种子数据生成完成，共 {} 个任务 / {} 条结果", taskCount, totalResults);
        return totalResults;
    }

    /** 构造单条评测结果 — 按 i 决定质量档位（优 40% / 中 40% / 差 20%） */
    private EvalResultEntity buildResult(String taskId, String evalType, int i, String createTime) {
        String query = QUERIES[i % QUERIES.length];
        String standard = ANSWERS[i % ANSWERS.length];
        // 实际答案 = 标准答案的轻微变体（模拟「接近但不完全相同」）
        String actual = ANSWERS[i % ANSWERS.length];

        // 三档质量：i%5 ∈ {0,1} 优秀(0.85-0.97)，{2,3} 中等(0.65-0.80)，{4} 较差(0.40-0.58)
        int band = i % 5;
        double base = (band <= 1) ? (0.85 + (i % 13) / 100.0)   // 优秀
                : (band <= 3) ? (0.65 + (i % 16) / 100.0)        // 中等
                : (0.40 + (i % 19) / 100.0);                     // 较差

        // 检索类指标（围绕 base 微调，保证相互合理）
        double recall = clamp(base + jitter(i, 0.05));
        double precision = clamp(base + jitter(i + 1, -0.03));
        double f1 = (recall + precision) == 0 ? 0 : 2 * recall * precision / (recall + precision);
        double top3 = band <= 3 ? 1.0 : 0.0; // 差档 top3 经常不命中
        double mrr = clamp(base - 0.05 + jitter(i, 0.04));
        double ndcg = clamp(base + jitter(i + 2, 0.02));
        double map = clamp(f1 + jitter(i, 0.03));
        double answerSim = clamp(base + jitter(i + 3, 0.06));

        // 上下文维度
        double ctxPrecision = clamp(base + jitter(i + 4, 0.04));
        double ctxRecall = clamp(base - 0.02 + jitter(i, 0.05));
        double ctxRelevance = clamp(base + jitter(i + 5, 0.03));

        // 生成类
        double faithfulness = clamp(base + 0.05 + jitter(i, 0.03));
        double relevance = clamp(base + jitter(i + 6, 0.04));
        double completeness = clamp(base + jitter(i + 7, 0.05));
        double answerCorrectness = clamp(base + jitter(i + 8, 0.04));
        double hallucinationRate = clamp(1.0 - faithfulness); // 忠实度越低幻觉越高
        int hallucinationFlag = hallucinationRate >= 0.3 ? 1 : 0;

        // 综合分（与 EvalExecutionService.computeOverall 口径一致，按 evalType 加权）
        double overall = computeOverall(evalType, recall, precision, top3, mrr, ndcg,
                faithfulness, relevance, completeness, answerCorrectness, hallucinationRate,
                answerSim, ctxPrecision, ctxRecall, ctxRelevance);

        return EvalResultEntity.builder()
                .taskId(taskId)
                .traceId("seed-trace-" + UUID.randomUUID().toString().substring(0, 8))
                .queryText(query)
                .standardAnswer(standard)
                .actualAnswer(actual)
                .recallScore(round(recall))
                .precisionScore(round(precision))
                .f1Score(round(f1))
                .top3HitRate(round(top3))
                .mrrScore(round(mrr))
                .ndcgScore(round(ndcg))
                .mapScore(round(map))
                .answerSimilarity(round(answerSim))
                .contextPrecision(round(ctxPrecision))
                .contextRecall(round(ctxRecall))
                .contextRelevance(round(ctxRelevance))
                .faithfulnessScore(round(faithfulness))
                .relevanceScore(round(relevance))
                .hallucinationFlag(hallucinationFlag)
                .completenessScore(round(completeness))
                .answerCorrectness(round(answerCorrectness))
                .overallScore(round(overall))
                .evalDetail("{\"seed\":true,\"band\":\"" + (band <= 1 ? "good" : band <= 3 ? "mid" : "poor") + "\"}")
                .createTime(createTime)
                .build();
    }

    /** 综合分加权（与 EvalExecutionService v2 口径对齐） */
    private double computeOverall(String evalType, double recall, double precision, double top3, double mrr, double ndcg,
                                  double faith, double rel, double comp, double correct, double halluc,
                                  double sim, double ctxP, double ctxR, double ctxRel) {
        switch (evalType) {
            case "RAG_RETRIEVAL":
                return clamp(f1Of(recall, precision) * 0.4 + top3 * 0.2 + mrr * 0.2 + ndcg * 0.2);
            case "ANSWER_QUALITY":
                return clamp(faith * 0.25 + rel * 0.25 + comp * 0.15 + sim * 0.05 + correct * 0.2 + (1 - halluc) * 0.1);
            case "CONTEXT_QUALITY":
                return clamp(ctxP * 0.4 + ctxR * 0.4 + ctxRel * 0.2);
            default:
                return clamp(faith * 0.4 + rel * 0.4 + (1 - halluc) * 0.2);
        }
    }

    private double f1Of(double r, double p) {
        return (r + p) == 0 ? 0 : 2 * r * p / (r + p);
    }

    /** 基于 i 的小幅扰动（确定性，保证可复现） */
    private double jitter(int i, double amp) {
        return (((i * 9301 + 49297) % 233280) / 233280.0 - 0.5) * 2 * amp;
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private String evalTypeLabel(String type) {
        switch (type) {
            case "RAG_RETRIEVAL": return "RAG检索";
            case "ANSWER_QUALITY": return "答案质量";
            case "CONTEXT_QUALITY": return "上下文质量";
            default: return type;
        }
    }
}
