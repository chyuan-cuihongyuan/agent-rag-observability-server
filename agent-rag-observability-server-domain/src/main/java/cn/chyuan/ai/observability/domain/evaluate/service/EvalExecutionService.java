package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IAnswerSourceProvider;
import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IEvalMetricsPort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmJudgePort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.AnswerSample;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.EvalDatasetItem;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.JudgeVerdict;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RetrievalMetrics;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 评测执行引擎 — 把一个评测任务真正“跑起来”：
 * 解析数据集 → 逐条获取实际答案 → 计算确定性检索指标 + LLM 答案质量评判 →
 * 按评测类型加权综合分 → 批量落库结果 → 周期回写进度 → 完成/失败置位。
 * <p>
 * 由 EvaluateService 在线程池中异步调用，本身不开线程。
 */
@Slf4j
@Service
public class EvalExecutionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 每累计多少条回写一次进度 */
    private static final int PROGRESS_BATCH = 10;

    private final IEvalTaskRepository evalTaskRepository;
    private final IEvalResultRepository evalResultRepository;
    private final IEvalDatasetRepository evalDatasetRepository;
    private final RetrievalMetricCalculator metricCalculator;
    private final IAnswerSourceProvider answerSourceProvider;
    private final ILlmJudgePort llmJudgePort;
    private final IEvalMetricsPort evalMetricsPort;

    public EvalExecutionService(IEvalTaskRepository evalTaskRepository,
                                IEvalResultRepository evalResultRepository,
                                IEvalDatasetRepository evalDatasetRepository,
                                RetrievalMetricCalculator metricCalculator,
                                IAnswerSourceProvider answerSourceProvider,
                                ILlmJudgePort llmJudgePort,
                                IEvalMetricsPort evalMetricsPort) {
        this.evalTaskRepository = evalTaskRepository;
        this.evalResultRepository = evalResultRepository;
        this.evalDatasetRepository = evalDatasetRepository;
        this.metricCalculator = metricCalculator;
        this.answerSourceProvider = answerSourceProvider;
        this.llmJudgePort = llmJudgePort;
        this.evalMetricsPort = evalMetricsPort;
    }

    /**
     * 执行一个评测任务（同步阻塞，调用方负责放入线程池）。
     */
    public void execute(EvalTaskEntity task) {
        String taskId = task.getTaskId();
        long startMs = System.currentTimeMillis();
        try {
            List<EvalDatasetItem> items = loadDatasetItems(task.getDatasetId());
            if (items.isEmpty()) {
                log.warn("评测任务无可执行条目, taskId={}, datasetId={}", taskId, task.getDatasetId());
                evalTaskRepository.updateStatus(taskId, "FAILED");
                evalMetricsPort.recordTaskFinished(task.getEvalType(), "FAILED");
                return;
            }

            String evalType = task.getEvalType() == null ? "ANSWER_QUALITY" : task.getEvalType();
            int total = items.size();
            evalTaskRepository.updateTotalCount(taskId, total);
            int completed = 0;
            double sumOverall = 0.0;
            List<EvalResultEntity> buffer = new ArrayList<>();

            for (EvalDatasetItem item : items) {
                EvalResultEntity result = evaluateOne(taskId, evalType, item);
                buffer.add(result);
                completed++;
                sumOverall += result.getOverallScore() == null ? 0.0 : result.getOverallScore();
                evalMetricsPort.recordScore(evalType, result.getOverallScore());
                if (result.getHallucinationFlag() != null && result.getHallucinationFlag() == 1) {
                    evalMetricsPort.recordHallucination(evalType);
                }

                // 分批落库 + 回写进度，避免长任务内存堆积、前端长时间无进度
                if (buffer.size() >= PROGRESS_BATCH) {
                    flush(buffer, taskId, completed, sumOverall);
                    buffer.clear();
                }
            }
            if (!buffer.isEmpty()) {
                flush(buffer, taskId, completed, sumOverall);
                buffer.clear();
            }

            double avgOverall = total == 0 ? 0.0 : sumOverall / total;
            evalTaskRepository.updateProgress(taskId, completed, round(avgOverall));
            evalTaskRepository.updateStatus(taskId, "COMPLETED");
            evalMetricsPort.recordTaskFinished(evalType, "COMPLETED");
            evalMetricsPort.recordTaskDuration(evalType, System.currentTimeMillis() - startMs);
            log.info("评测任务完成, taskId={}, 条目={}, 平均综合分={}", taskId, total, round(avgOverall));
        } catch (Exception e) {
            log.error("评测任务执行失败, taskId={}", taskId, e);
            try {
                evalTaskRepository.updateStatus(taskId, "FAILED");
                evalMetricsPort.recordTaskFinished(task.getEvalType(), "FAILED");
            } catch (Exception ignored) {
                // 置失败本身异常忽略
            }
        }
    }

    /** 评测单条样本 */
    private EvalResultEntity evaluateOne(String taskId, String evalType, EvalDatasetItem item) {
        String query = item.getQuery();
        String standardAnswer = item.getStandardAnswer();
        List<String> standardChunks = item.getStandardChunks() == null ? List.of() : item.getStandardChunks();

        AnswerSample sample = null;
        try {
            sample = answerSourceProvider.fetch(query, null);
        } catch (Exception e) {
            log.warn("获取实际答案失败, query={}, err={}", query, e.getMessage());
        }

        String traceId = sample == null ? "" : nullToEmpty(sample.getTraceId());
        String actualAnswer = sample == null ? "" : nullToEmpty(sample.getActualAnswer());
        List<String> actualChunks = sample == null || sample.getRetrievedChunks() == null
                ? List.of() : sample.getRetrievedChunks();

        // 1. 确定性检索指标
        RetrievalMetrics rm = metricCalculator.compute(standardChunks, actualChunks, standardAnswer, actualAnswer);

        // 2. LLM 答案质量评判（按需）
        JudgeVerdict verdict = null;
        boolean needJudge = !"RAG_RETRIEVAL".equals(evalType);
        if (needJudge) {
            try {
                verdict = llmJudgePort.judge(query, standardAnswer, actualAnswer, actualChunks);
            } catch (Exception e) {
                log.warn("LLM 评判失败, query={}, err={}", query, e.getMessage());
            }
        }

        double faithfulness = verdict == null ? 0.0 : verdict.getFaithfulness();
        double relevance = verdict == null ? 0.0 : verdict.getRelevance();
        double hallucinationRate = verdict == null ? 0.0 : verdict.getHallucinationRate();
        double completeness = verdict == null ? 0.0 : verdict.getCompleteness();
        double answerCorrectness = verdict == null ? 0.0 : verdict.getAnswerCorrectness();
        double contextPrecision = verdict == null ? 0.0 : verdict.getContextPrecision();
        double contextRecall = verdict == null ? 0.0 : verdict.getContextRecall();
        double contextRelevance = verdict == null ? 0.0 : verdict.getContextRelevance();
        // 语义相似度优先用 LLM 评判值，降级用词面相似度
        double similarity = (verdict != null && verdict.getSimilarity() > 0)
                ? verdict.getSimilarity() : rm.getAnswerSimilarity();
        int hallucinationFlag = hallucinationRate >= 0.3 ? 1 : 0;

        // 3. 工具调用评测（新增）
        Double toolSelectionScore = null;
        Double toolParamScore = null;
        Double toolCallScore = null;
        if ("TOOL_CALL".equals(evalType) && item.getExpectedTools() != null) {
            ToolCallMetrics tcm = evaluateToolCall(sample, item);
            toolSelectionScore = tcm.selectionScore;
            toolParamScore = tcm.paramScore;
            toolCallScore = tcm.overallScore;
        }

        // 4. Agent 决策评测（新增）
        Double intentScore = null;
        Double branchScore = null;
        Double reasoningScore = null;
        Double agentDecisionScore = null;
        if ("AGENT_DECISION".equals(evalType) && (item.getExpectedIntentType() != null || item.getExpectedBranchType() != null)) {
            AgentDecisionMetrics adm = evaluateAgentDecision(sample, item);
            intentScore = adm.intentScore;
            branchScore = adm.branchScore;
            reasoningScore = adm.reasoningScore;
            agentDecisionScore = adm.overallScore;
        }

        // 5. 按评测类型加权综合分（纳入新增指标：mrr/ndcg/context 维度/answerCorrectness）
        double overall = computeOverall(evalType, rm, faithfulness, relevance, hallucinationRate,
                completeness, similarity, answerCorrectness, contextPrecision, contextRecall,
                contextRelevance, toolCallScore, agentDecisionScore);

        // 6. 组装明细
        JSONObject detail = new JSONObject();
        detail.put("evalType", evalType);
        // 综合分权重版本，便于横向对比区分口径（v2 纳入 mrr/ndcg/context/answerCorrectness）
        detail.put("weightVersion", "v2");
        detail.put("retrievalCount", actualChunks.size());
        detail.put("standardChunkCount", standardChunks.size());
        if (verdict != null) {
            detail.put("judgeDegraded", verdict.isDegraded());
            detail.put("judgeDetail", verdict.getDetail());
        }
        if (toolCallScore != null) {
            detail.put("toolSelectionScore", toolSelectionScore);
            detail.put("toolParamScore", toolParamScore);
        }
        if (agentDecisionScore != null) {
            detail.put("intentScore", intentScore);
            detail.put("branchScore", branchScore);
            detail.put("reasoningScore", reasoningScore);
        }

        return EvalResultEntity.builder()
                .taskId(taskId)
                .traceId(traceId)
                .queryText(query)
                .standardAnswer(standardAnswer)
                .actualAnswer(actualAnswer)
                .recallScore(rm.getRecall())
                .precisionScore(rm.getPrecision())
                .f1Score(rm.getF1())
                .top3HitRate(rm.getTop3HitRate())
                .mrrScore(rm.getMrr())
                .ndcgScore(rm.getNdcg())
                .mapScore(rm.getMap())
                .answerSimilarity(round(similarity))
                .contextPrecision(verdict == null ? null : round(contextPrecision))
                .contextRecall(verdict == null ? null : round(contextRecall))
                .contextRelevance(verdict == null ? null : round(contextRelevance))
                .faithfulnessScore(round(faithfulness))
                .relevanceScore(round(relevance))
                .hallucinationFlag(hallucinationFlag)
                .completenessScore(round(completeness))
                .answerCorrectness(verdict == null ? null : round(answerCorrectness))
                .overallScore(round(overall))
                .evalDetail(detail.toJSONString())
                .createTime(LocalDateTime.now().format(FMT))
                .toolSelectionScore(toolSelectionScore != null ? round(toolSelectionScore) : null)
                .toolParamScore(toolParamScore != null ? round(toolParamScore) : null)
                .toolCallScore(toolCallScore != null ? round(toolCallScore) : null)
                .intentScore(intentScore != null ? round(intentScore) : null)
                .branchScore(branchScore != null ? round(branchScore) : null)
                .reasoningScore(reasoningScore != null ? round(reasoningScore) : null)
                .agentDecisionScore(agentDecisionScore != null ? round(agentDecisionScore) : null)
                .build();
    }

    /**
     * 综合分加权策略（v2，纳入新增指标），按评测类型侧重不同维度：
     * <ul>
     *   <li>RAG_RETRIEVAL：检索质量（F1 0.4 + Top3 0.2 + MRR 0.2 + NDCG 0.2）</li>
     *   <li>ANSWER_QUALITY：答案质量（忠实 0.25 + 相关 0.25 + 完整 0.15 + 相似 0.05 + 正确性 0.2 + 幻觉惩罚 0.1）
     *       —— 正确性 0.2 为新增，其余项相对 v1 等比缩减</li>
     *   <li>CONTEXT_QUALITY：上下文维度（精确率 0.4 + 召回率 0.4 + 相关性 0.2）</li>
     *   <li>TOOL_CALL：工具调用质量（工具选择 0.5 + 参数正确 0.5）</li>
     *   <li>AGENT_DECISION：决策质量（意图 0.3 + 分支 0.3 + 推理 0.4）</li>
     *   <li>默认：检索 0.4 + 上下文 0.2 + 质量 0.4</li>
     * </ul>
     * 注：权重版本随结果写入 eval_detail.weightVersion，便于横向对比时区分口径。
     */
    private double computeOverall(String evalType, RetrievalMetrics rm, double faithfulness,
                                  double relevance, double hallucinationRate, double completeness,
                                  double similarity, double answerCorrectness,
                                  double contextPrecision, double contextRecall, double contextRelevance,
                                  Double toolCallScore, Double agentDecisionScore) {
        switch (evalType) {
            case "RAG_RETRIEVAL":
                return rm.getF1() * 0.4 + rm.getTop3HitRate() * 0.2 + rm.getMrr() * 0.2 + rm.getNdcg() * 0.2;
            case "ANSWER_QUALITY":
                return clamp(faithfulness * 0.25 + relevance * 0.25 + completeness * 0.15
                        + similarity * 0.05 + answerCorrectness * 0.2 + (1 - hallucinationRate) * 0.1);
            case "CONTEXT_QUALITY":
                return clamp(contextPrecision * 0.4 + contextRecall * 0.4 + contextRelevance * 0.2);
            case "TOOL_CALL":
                return toolCallScore != null ? toolCallScore : 0.0;
            case "AGENT_DECISION":
                return agentDecisionScore != null ? agentDecisionScore : 0.0;
            default:
                double retrievalScore = rm.getF1() * 0.4 + rm.getTop3HitRate() * 0.2
                        + rm.getMrr() * 0.2 + rm.getNdcg() * 0.2;
                double contextScore = clamp(contextPrecision * 0.4 + contextRecall * 0.4 + contextRelevance * 0.2);
                double qualityScore = clamp(faithfulness * 0.35 + relevance * 0.35
                        + (1 - hallucinationRate) * 0.3);
                return retrievalScore * 0.4 + contextScore * 0.2 + qualityScore * 0.4;
        }
    }

    /**
     * 评测工具调用质量
     */
    private ToolCallMetrics evaluateToolCall(AnswerSample sample, EvalDatasetItem item) {
        List<String> expectedTools = item.getExpectedTools();
        List<String> actualTools = sample == null ? List.of() : sample.getToolCalls();

        // 工具选择正确率：计算期望工具和实际工具的匹配度
        double selectionScore = 0.0;
        if (expectedTools != null && !expectedTools.isEmpty()) {
            long matchCount = expectedTools.stream()
                    .filter(actualTools::contains)
                    .count();
            selectionScore = (double) matchCount / expectedTools.size();
        }

        // 工具参数正确率：简化实现，基于参数匹配
        double paramScore = 0.0;
        if (item.getExpectedToolParams() != null && sample != null && sample.getToolParams() != null) {
            int totalParams = item.getExpectedToolParams().size();
            int matchParams = 0;
            for (Map.Entry<String, Object> entry : item.getExpectedToolParams().entrySet()) {
                Object actualValue = sample.getToolParams().get(entry.getKey());
                if (actualValue != null && actualValue.equals(entry.getValue())) {
                    matchParams++;
                }
            }
            paramScore = totalParams > 0 ? (double) matchParams / totalParams : 0.0;
        }

        double overallScore = selectionScore * 0.5 + paramScore * 0.5;

        return new ToolCallMetrics(selectionScore, paramScore, overallScore);
    }

    /**
     * 评测 Agent 决策质量
     */
    private AgentDecisionMetrics evaluateAgentDecision(AnswerSample sample, EvalDatasetItem item) {
        // 意图识别正确率
        double intentScore = 0.0;
        if (item.getExpectedIntentType() != null && sample != null) {
            intentScore = item.getExpectedIntentType().equals(sample.getIntentType()) ? 1.0 : 0.0;
        }

        // 分支选择正确率
        double branchScore = 0.0;
        if (item.getExpectedBranchType() != null && sample != null) {
            branchScore = item.getExpectedBranchType().equals(sample.getBranchType()) ? 1.0 : 0.0;
        }

        // 推理质量分：基于 LLM 评判或简化实现
        double reasoningScore = 0.0;
        if (item.getExpectedReasoningSteps() != null && sample != null && sample.getReasoningSteps() != null) {
            // 简化实现：基于步骤匹配度
            String[] expectedSteps = item.getExpectedReasoningSteps().split(";");
            String[] actualSteps = sample.getReasoningSteps().split(";");
            int matchCount = 0;
            for (String expected : expectedSteps) {
                for (String actual : actualSteps) {
                    if (actual.contains(expected.trim())) {
                        matchCount++;
                        break;
                    }
                }
            }
            reasoningScore = expectedSteps.length > 0 ? (double) matchCount / expectedSteps.length : 0.0;
        }

        double overallScore = intentScore * 0.3 + branchScore * 0.3 + reasoningScore * 0.4;

        return new AgentDecisionMetrics(intentScore, branchScore, reasoningScore, overallScore);
    }

    /** 工具调用评测指标 */
    private record ToolCallMetrics(double selectionScore, double paramScore, double overallScore) {}

    /** Agent 决策评测指标 */
    private record AgentDecisionMetrics(double intentScore, double branchScore, double reasoningScore, double overallScore) {}

    private void flush(List<EvalResultEntity> buffer, String taskId, int completed, double sumOverall) {
        evalResultRepository.batchSave(new ArrayList<>(buffer));
        double runningAvg = completed == 0 ? 0.0 : sumOverall / completed;
        evalTaskRepository.updateProgress(taskId, completed, round(runningAvg));
    }

    /** 解析数据集 itemsJson 为条目列表 */
    private List<EvalDatasetItem> loadDatasetItems(String datasetId) {
        if (datasetId == null || datasetId.isEmpty()) {
            return Collections.emptyList();
        }
        EvalDatasetEntity dataset = evalDatasetRepository.queryByDatasetId(datasetId);
        if (dataset == null || dataset.getItemsJson() == null || dataset.getItemsJson().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<EvalDatasetItem> items = JSON.parseArray(dataset.getItemsJson(), EvalDatasetItem.class);
            return items == null ? Collections.emptyList() : items;
        } catch (Exception e) {
            log.error("解析数据集 itemsJson 失败, datasetId={}", datasetId, e);
            return Collections.emptyList();
        }
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
