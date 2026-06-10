package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.cache.ICachePort;
import cn.chyuan.ai.observability.domain.observe.model.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据填充服务
 * 生成模拟的 Trace 数据写入 ES，用于仪表盘展示和开发调试
 */
@Slf4j
@Service
public class DataSeedService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObserveCollectService observeCollectService;
    private final ICachePort cachePort;

    public DataSeedService(ObserveCollectService observeCollectService, ICachePort cachePort) {
        this.observeCollectService = observeCollectService;
        this.cachePort = cachePort;
    }

    // ===== 模拟数据池 =====

    /** 分支类型权重：RAG 60%、DIRECT_ANSWER 20%、TOOL_CALL 15%、REJECT 5% */
    private static final String[] BRANCH_TYPES = {"RAG", "RAG", "RAG", "RAG", "RAG", "RAG",
            "DIRECT_ANSWER", "DIRECT_ANSWER",
            "TOOL_CALL", "TOOL_CALL", "TOOL_CALL",
            "REJECT"};

    /** Agent 状态权重：SUCCESS 85%、FAIL 10%、TIMEOUT 3%、LOOP 2% */
    private static final String[] AGENT_STATUSES = {"SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS",
            "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS",
            "FAIL", "FAIL",
            "TIMEOUT", "LOOP"};

    /** 工具名称池 */
    private static final String[] TOOL_NAMES = {
            "prometheus_query", "loki_search", "milvus_search",
            "knowledge_base_query", "alert_manager", "log_analyzer",
            "metric_aggregator", "topology_discovery", "anomaly_detector"
    };

    /** 用户查询模拟池 */
    private static final String[] USER_QUERIES = {
            "查询最近1小时CPU使用率超过80%的服务器",
            "分析今天凌晨的告警风暴根因",
            "检索微服务 order-service 的部署架构文档",
            "帮我查看 Redis 连接池的监控指标",
            "分析过去24小时的应用日志错误趋势",
            "检查数据库慢查询 TOP 10",
            "搜索 Kafka 消费延迟的排查方案",
            "查询网关层 5xx 错误的分布情况",
            "分析容器 OOMKilled 事件的频率",
            "检索 JVM GC 调优的最佳实践",
            "帮我查看 Kubernetes 集群资源使用率",
            "分析 Nginx 访问日志中的异常请求",
            "查询 MySQL 主从同步延迟状态",
            "检索 ELK 日志采集 pipeline 配置方法",
            "分析服务间调用链路的性能瓶颈",
            "查看 Docker 镜像构建的优化建议",
            "搜索服务降级和熔断的设计模式",
            "查询 Prometheus 告警规则配置模板"
    };

    /** 错误信息模拟池 */
    private static final String[] ERROR_MESSAGES = {
            "LLM 调用超时，响应时间超过 30s",
            "向量检索服务不可用，Milvus 连接失败",
            "工具调用返回空结果，prometheus 无数据",
            "意图识别失败，无法解析用户查询",
            "RAG 检索结果为空，知识库无匹配文档",
            "模型推理异常：token 超出上下文长度限制",
            "下游服务 order-service 返回 503",
            "重试次数耗尽，工具调用连续失败 3 次"
    };

    /** 模型版本 */
    private static final String[] MODEL_VERSIONS = {"deepseek-v4-pro", "glm-4-flash", "qwen-plus"};

    /** 来源服务 */
    private static final String[] SOURCE_SERVICES = {"aiops-agent", "mcp-gateway", "rag-service"};

    /** Agent ID */
    private static final String[] AGENT_IDS = {"agent-aiops-001", "agent-aiops-002", "agent-rag-001", "agent-mcp-001"};

    /** 回答模板 */
    private static final String[] ANSWERS = {
            "根据监控数据分析，最近1小时有3台服务器CPU使用率超过80%，分别是：10.0.1.15（92%）、10.0.1.23（87%）、10.0.2.8（83%）。建议检查是否有异常进程占用资源。",
            "告警风暴的根因分析如下：凌晨2:15由于网络抖动导致 Prometheus 误触发200+条告警，实际需要关注的是数据库连接池耗尽问题，建议调整告警聚合规则。",
            "order-service 的部署架构文档已检索到，该服务采用 Spring Cloud 微服务架构，包含网关层、服务层和数据层三部分，详见知识库文档 KB-2024-0089。",
            "Redis 连接池监控指标：当前活跃连接数 48/200（24%），等待队列长度 0，平均获取连接耗时 2ms，连接池状态健康。",
            "过去24小时的日志错误趋势：总计 1,247 条错误，主要集中在 14:00-16:00 时段，NullPointerException 占 43%，TimeoutException 占 28%。",
            "慢查询 TOP 10 已生成，最慢查询耗时 12.3s（SELECT * FROM order_detail WHERE ...），建议添加组合索引 (tenant_id, create_time)。",
            "Kafka 消费延迟排查方案：建议检查消费者 group 的 Lag 指标，当前 consumer-group-1 的 Lag 为 15,230 条，可通过增加分区或消费者实例缓解。",
            "网关层 5xx 错误分布：过去1小时共 89 次，其中 503 占 65%（上游服务不可用），502 占 25%（网关超时），500 占 10%（程序异常）。",
            "分析完成，请查看详细报告。根据历史数据和当前趋势，建议进行以下优化操作。",
            "已检索到相关信息，以下是综合分析结果。如需更详细的排查方案，可以进一步查询知识库。"
    };

    /**
     * 生成并写入模拟数据
     *
     * @param days        天数（数据分布在过去 N 天内）
     * @param countPerDay 每天生成的 trace 数量
     * @return 生成的 trace 总数
     */
    public int seedData(int days, int countPerDay) {
        int totalTraces = 0;
        LocalDateTime now = LocalDateTime.now();

        for (int day = 0; day < days; day++) {
            for (int i = 0; i < countPerDay; i++) {
                // 在当天内均匀分布时间
                int minuteOffset = (int) ((long) i * 24 * 60 / countPerDay);
                LocalDateTime baseTime = now.minusDays(day).withHour(0).withMinute(0).withSecond(0)
                        .plusMinutes(minuteOffset);
                // 加入小幅随机偏移（±30秒）
                int jitter = (i * 7 + day * 13) % 60 - 30;
                LocalDateTime eventTime = baseTime.plusSeconds(jitter);
                String traceId = "seed-trace-" + UUID.randomUUID().toString().substring(0, 8);
                String sessionId = "seed-session-" + ((i + day * 100) % 20 + 1);
                int idx = (i + day * countPerDay);

                // 选择分支类型和状态
                String branchType = BRANCH_TYPES[idx % BRANCH_TYPES.length];
                String agentStatus = AGENT_STATUSES[idx % AGENT_STATUSES.length];

                // 生成 Agent 决策日志
                generateAgentDecision(traceId, sessionId, eventTime, branchType, agentStatus, idx);

                // 生成 RAG 检索日志（RAG 分支和部分 TOOL_CALL 分支）
                if ("RAG".equals(branchType) || (idx % 5 == 0)) {
                    generateRagRetrieval(traceId, sessionId, eventTime, idx);
                }

                // 生成聊天结果日志
                generateChatResult(traceId, sessionId, eventTime, agentStatus, idx);

                // 生成工具调用日志（TOOL_CALL 分支）
                if ("TOOL_CALL".equals(branchType)) {
                    int toolCount = (idx % 3) + 1;
                    for (int t = 0; t < toolCount; t++) {
                        generateToolCallLog(traceId, eventTime, t, agentStatus, idx);
                    }
                }

                // 生成记忆召回日志（部分 trace）
                if (idx % 3 == 0) {
                    generateMemoryRecallLog(traceId, eventTime, idx);
                }

                totalTraces++;
            }
        }

        // 清除仪表盘缓存，使新数据立即可见
        clearDashboardCache();

        log.info("数据填充完成，共生成 {} 条 Trace，时间范围：近 {} 天", totalTraces, days);
        return totalTraces;
    }

    /** 生成 Agent 决策日志 */
    private void generateAgentDecision(String traceId, String sessionId, LocalDateTime time,
                                       String branchType, String agentStatus, int idx) {
        // 根据分支类型选择工具列表
        String selectedToolList = "[]";
        if ("TOOL_CALL".equals(branchType)) {
            int tool1 = idx % TOOL_NAMES.length;
            int tool2 = (idx + 3) % TOOL_NAMES.length;
            selectedToolList = "[\"" + TOOL_NAMES[tool1] + "\",\"" + TOOL_NAMES[tool2] + "\"]";
        } else if ("RAG".equals(branchType)) {
            selectedToolList = "[\"milvus_search\",\"knowledge_base_query\"]";
        }

        // 失败时设置错误信息
        String errorMessage = null;
        if ("FAIL".equals(agentStatus)) {
            errorMessage = ERROR_MESSAGES[idx % ERROR_MESSAGES.length];
        } else if ("TIMEOUT".equals(agentStatus)) {
            errorMessage = "LLM 调用超时，响应时间超过 30s";
        }

        // 耗时：成功 200~3000ms，失败 500~8000ms
        int costTimeMs;
        if ("SUCCESS".equals(agentStatus)) {
            costTimeMs = 200 + (idx * 37) % 2800;
        } else {
            costTimeMs = 500 + (idx * 53) % 7500;
        }

        AgentDecisionEntity entity = AgentDecisionEntity.builder()
                .traceId(traceId)
                .sourceService(SOURCE_SERVICES[idx % SOURCE_SERVICES.length])
                .tenantId("tenant-default")
                .ownerUserId("user-" + ((idx % 10) + 1))
                .sessionId(sessionId)
                .agentId(AGENT_IDS[idx % AGENT_IDS.length])
                .userQuery(USER_QUERIES[idx % USER_QUERIES.length])
                .intentType("RAG".equals(branchType) ? "KNOWLEDGE_QA" : "TOOL_CALL".equals(branchType) ? "TOOL_OPERATION" : "DIRECT_CHAT")
                .selectedToolList(selectedToolList)
                .decisionReason("基于用户意图分析，选择" + branchType + "分支处理")
                .branchType(branchType)
                .toolCallTimes("TOOL_CALL".equals(branchType) ? (idx % 3) + 1 : 0)
                .toolRetryTimes("TOOL_CALL".equals(branchType) && "FAIL".equals(agentStatus) ? (idx % 2) + 1 : 0)
                .agentStatus(agentStatus)
                .costTimeMs(costTimeMs)
                .modelVersion(MODEL_VERSIONS[idx % MODEL_VERSIONS.length])
                .errorMessage(errorMessage)
                .createTime(time.format(FMT))
                .build();
        observeCollectService.collectAgentDecision(entity);
    }

    /** 生成 RAG 检索日志 */
    private void generateRagRetrieval(String traceId, String sessionId, LocalDateTime time, int idx) {
        // 约 15% 的检索为空检索
        boolean isEmpty = (idx % 7 == 0);
        int retrievalCount = isEmpty ? 0 : (idx % 8) + 1;
        int topK = 5 + (idx % 6);

        // 检索耗时 50~800ms
        int retrievalCostMs = 50 + (idx * 23) % 750;

        // 模拟 rerank 分数
        String rerankScores = isEmpty ? "[]" : generateScoreList(retrievalCount);

        RagRetrievalEntity entity = RagRetrievalEntity.builder()
                .traceId(traceId)
                .sourceService("rag-service")
                .tenantId("tenant-default")
                .ownerUserId("user-" + ((idx % 10) + 1))
                .sessionId(sessionId)
                .agentId(AGENT_IDS[idx % AGENT_IDS.length])
                .queryText(USER_QUERIES[idx % USER_QUERIES.length])
                .rewriteText("优化查询：" + USER_QUERIES[idx % USER_QUERIES.length])
                .retrievalTopk(topK)
                .retrievalCount(retrievalCount)
                .sourceDocs(isEmpty ? "[]" : "[\"KB-DOC-" + (idx % 50) + "\",\"KB-DOC-" + ((idx + 1) % 50) + "\"]")
                .rerankScores(rerankScores)
                .emptyRetrieval(isEmpty ? 1 : 0)
                .retrievalCostMs(retrievalCostMs)
                .retrievalStages("rewrite,embed,search,rerank")
                .ragStrategyVersion("v2.1")
                .createTime(time.format(FMT))
                .build();
        observeCollectService.collectRagRetrieval(entity);
    }

    /** 生成聊天结果日志 */
    private void generateChatResult(String traceId, String sessionId, LocalDateTime time,
                                    String agentStatus, int idx) {
        String finalStatus = "SUCCESS".equals(agentStatus) ? "SUCCESS" :
                "LOOP".equals(agentStatus) ? "SUCCESS" : "FAIL";

        // 总耗时：300~5000ms
        int totalCostTimeMs = 300 + (idx * 41) % 4700;
        int promptTokens = 500 + (idx * 17) % 2000;
        int completionTokens = 100 + (idx * 11) % 900;

        ChatResultEntity entity = ChatResultEntity.builder()
                .traceId(traceId)
                .sourceService(SOURCE_SERVICES[idx % SOURCE_SERVICES.length])
                .tenantId("tenant-default")
                .ownerUserId("user-" + ((idx % 10) + 1))
                .sessionId(sessionId)
                .agentId(AGENT_IDS[idx % AGENT_IDS.length])
                .question(USER_QUERIES[idx % USER_QUERIES.length])
                .answer("FAIL".equals(finalStatus) ? "" : ANSWERS[idx % ANSWERS.length])
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalCostTimeMs(totalCostTimeMs)
                .finalStatus(finalStatus)
                .modelVersion(MODEL_VERSIONS[idx % MODEL_VERSIONS.length])
                .createTime(time.format(FMT))
                .build();
        observeCollectService.collectChatResult(entity);
    }

    /** 生成工具调用日志 */
    private void generateToolCallLog(String traceId, LocalDateTime time, int order,
                                     String agentStatus, int idx) {
        String toolName = TOOL_NAMES[(idx + order) % TOOL_NAMES.length];
        boolean isFail = "FAIL".equals(agentStatus) && order == 0;
        String status = isFail ? "FAIL" : "SUCCESS";
        int costTimeMs = isFail ? 1000 + (idx * 31) % 4000 : 100 + (idx * 19) % 900;

        ToolCallLogEntity entity = ToolCallLogEntity.builder()
                .traceId(traceId)
                .spanId("span-" + UUID.randomUUID().toString().substring(0, 8))
                .parentSpanId("span-parent-001")
                .toolName(toolName)
                .toolInput("{\"query\":\"" + USER_QUERIES[idx % USER_QUERIES.length] + "\"}")
                .toolOutput(isFail ? "" : "{\"result\":\"ok\",\"data\":[1,2,3]}")
                .status(status)
                .costTimeMs(costTimeMs)
                .errorMessage(isFail ? ERROR_MESSAGES[idx % ERROR_MESSAGES.length] : null)
                .callOrder(order + 1)
                .createTime(time.format(FMT))
                .build();
        observeCollectService.collectToolCallLog(entity);
    }

    /** 生成记忆召回日志 */
    private void generateMemoryRecallLog(String traceId, LocalDateTime time, int idx) {
        MemoryRecallLogEntity entity = MemoryRecallLogEntity.builder()
                .traceId(traceId)
                .queryText(USER_QUERIES[idx % USER_QUERIES.length])
                .sessionMemoryCount(idx % 5)
                .agentMemoryCount(idx % 3)
                .sessionMemoryScores(generateDoubleScores(idx % 5))
                .agentMemoryScores(generateDoubleScores(idx % 3))
                .injectContent("已注入 " + (idx % 5) + " 条会话记忆和 " + (idx % 3) + " 条 Agent 记忆")
                .costTimeMs(10 + (idx * 7) % 90)
                .createTime(time.format(FMT))
                .build();
        observeCollectService.collectMemoryRecallLog(entity);
    }

    /** 生成 rerank 分数字符串 */
    private String generateScoreList(int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(count, 5); i++) {
            if (i > 0) sb.append(",");
            double score = 0.6 + ((i * 7 + 3) % 40) / 100.0;
            sb.append(String.format("%.2f", score));
        }
        sb.append("]");
        return sb.toString();
    }

    /** 生成记忆相似度分数列表 */
    private List<Double> generateDoubleScores(int count) {
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            scores.add(0.7 + ((i * 13 + 5) % 30) / 100.0);
        }
        return scores;
    }

    /** 清除仪表盘相关的缓存 */
    private void clearDashboardCache() {
        String[] cacheKeys = {
                "overview:1", "overview:3", "overview:7", "overview:30",
                "trend:1:hour", "trend:3:day", "trend:7:day", "trend:30:day",
                "branch:1", "branch:3", "branch:7", "branch:30",
                "tool:1", "tool:3", "tool:7", "tool:30"
        };
        for (String key : cacheKeys) {
            try {
                cachePort.delete(key);
            } catch (Exception e) {
                log.debug("清除缓存失败: {}", key);
            }
        }
    }
}
