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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测执行引擎 — 把一个评测任务真正“跑起来”：
 * 解析数据集 → 逐条获取实际答案 → 计算确定性检索指标 + LLM 答案质量评判 →
 * 按评测类型加权综合分 → 批量落库结果 → 周期回写进度 → 完成/失败置位。
 * <p>
 * 由 EvaluateService 在线程池中异步调用，本身不开线程。
 * <p>
 * 工单 0133 R1 改造：综合分权重从 Rubric 读取（RubricService.resolveWeights，
 * evalType → 启用中 Rubric，缺省回退内置 v2 兜底口径）；LLM 评判解析失败/降级的
 * unknown 维度占比进样本明细（eval_detail.unknownRatio）与任务级指标（eval_unknown_ratio）。
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
    private final RubricService rubricService;

    public EvalExecutionService(IEvalTaskRepository evalTaskRepository,
                                IEvalResultRepository evalResultRepository,
                                IEvalDatasetRepository evalDatasetRepository,
                                RetrievalMetricCalculator metricCalculator,
                                IAnswerSourceProvider answerSourceProvider,
                                ILlmJudgePort llmJudgePort,
                                IEvalMetricsPort evalMetricsPort,
                                RubricService rubricService) {
        this.evalTaskRepository = evalTaskRepository;
        this.evalResultRepository = evalResultRepository;
        this.evalDatasetRepository = evalDatasetRepository;
        this.metricCalculator = metricCalculator;
        this.answerSourceProvider = answerSourceProvider;
        this.llmJudgePort = llmJudgePort;
        this.evalMetricsPort = evalMetricsPort;
        this.rubricService = rubricService;
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
            // 综合分权重按 evalType 从 Rubric 解析一次（任务内复用；仓储异常时内置兜底）
            Map<String, Double> weights = rubricService.resolveWeights(evalType);
            int total = items.size();
            evalTaskRepository.updateTotalCount(taskId, total);
            int completed = 0;
            double sumOverall = 0.0;
            long totalUnknownDims = 0;
            long totalJudgeDims = 0;
            List<EvalResultEntity> buffer = new ArrayList<>();

            for (EvalDatasetItem item : items) {
                EvalSample sample = evaluateOne(taskId, evalType, weights, item);
                EvalResultEntity result = sample.result();
                buffer.add(result);
                completed++;
                sumOverall += result.getOverallScore() == null ? 0.0 : result.getOverallScore();
                totalUnknownDims += sample.unknownDims();
                totalJudgeDims += sample.judgeDims();
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
            // 任务级 unknown 占比（R1：「标准不清晰」可度量；仅含 LLM 评判维度）
            if (totalJudgeDims > 0) {
                evalMetricsPort.recordUnknownRatio(evalType, round((double) totalUnknownDims / totalJudgeDims));
            }
            log.info("评测任务完成, taskId={}, 条目={}, 平均综合分={}, unknown维度占比={}",
                    taskId, total, round(avgOverall),
                    totalJudgeDims == 0 ? 0.0 : round((double) totalUnknownDims / totalJudgeDims));
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
    private EvalSample evaluateOne(String taskId, String evalType, Map<String, Double> weights, EvalDatasetItem item) {
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

        // unknown 维度统计（样本级；needJudge=false 的纯检索类型无 LLM 维度，不计）
        int unknownDims = verdict == null || verdict.getUnknownKeys() == null
                ? 0 : verdict.getUnknownKeys().size();
        int judgeDims = needJudge ? BuiltinRubrics.JUDGE_TEMPLATES.size() : 0;

        // 3. 工具调用评测（新增）
        Double toolSelectionScore = null;
        Double toolParamScore = null;
        Double toolCallScore = null;
        ToolCallMetrics tcm = null;
        if ("TOOL_CALL".equals(evalType) && item.getExpectedTools() != null) {
            tcm = evaluateToolCall(sample, item);
            toolSelectionScore = tcm.selectionScore;
            toolParamScore = tcm.paramScore;
            toolCallScore = tcm.overallScore;
        }

        // 4. Agent 决策评测（新增）
        Double intentScore = null;
        Double branchScore = null;
        Double reasoningScore = null;
        Double agentDecisionScore = null;
        AgentDecisionMetrics adm = null;
        if ("AGENT_DECISION".equals(evalType) && (item.getExpectedIntentType() != null || item.getExpectedBranchType() != null)) {
            adm = evaluateAgentDecision(sample, item);
            intentScore = adm.intentScore;
            branchScore = adm.branchScore;
            reasoningScore = adm.reasoningScore;
            agentDecisionScore = adm.overallScore;
        }

        // 5. 按评测类型加权综合分（权重从 Rubric 读取；v2 口径迁移，key 与维度权重表对齐）
        Map<String, Double> metricScores = buildMetricScores(rm, faithfulness, relevance, hallucinationRate,
                completeness, similarity, answerCorrectness, contextPrecision, contextRecall,
                contextRelevance, tcm, adm);
        double overall = computeOverall(weights, metricScores);

        // 6. 组装明细
        JSONObject detail = new JSONObject();
        detail.put("evalType", evalType);
        // 综合分权重版本，便于横向对比区分口径（v2 纳入 mrr/ndcg/context/answerCorrectness；
        // v2-rubric 权重改由 Rubric 配置驱动，口径与 v2 内置种子一致）
        detail.put("weightVersion", "v2-rubric");
        detail.put("retrievalCount", actualChunks.size());
        detail.put("standardChunkCount", standardChunks.size());
        detail.put("unknownDimensionCount", unknownDims);
        detail.put("judgeDimensionCount", judgeDims);
        if (needJudge) {
            detail.put("unknownRatio", judgeDims == 0 ? 0.0 : round((double) unknownDims / judgeDims));
        }
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

        EvalResultEntity result = EvalResultEntity.builder()
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
        return new EvalSample(result, unknownDims, judgeDims);
    }

    /**
     * 汇总本次样本可用的全部指标分数（key 与 Rubric 维度 key 对齐）。
     * hallucination 为反向维度（越高越差），按 (1-rate) 计入综合分。
     */
    private Map<String, Double> buildMetricScores(RetrievalMetrics rm, double faithfulness, double relevance,
                                                  double hallucinationRate, double completeness, double similarity,
                                                  double answerCorrectness, double contextPrecision,
                                                  double contextRecall, double contextRelevance,
                                                  ToolCallMetrics tcm, AgentDecisionMetrics adm) {
        Map<String, Double> scores = new HashMap<>();
        scores.put("f1", rm.getF1());
        scores.put("top3HitRate", rm.getTop3HitRate());
        scores.put("mrr", rm.getMrr());
        scores.put("ndcg", rm.getNdcg());
        scores.put(BuiltinRubrics.KEY_FAITHFULNESS, faithfulness);
        scores.put(BuiltinRubrics.KEY_RELEVANCY, relevance);
        scores.put(BuiltinRubrics.KEY_HALLUCINATION, 1.0 - hallucinationRate);
        scores.put(BuiltinRubrics.KEY_COMPLETENESS, completeness);
        scores.put(BuiltinRubrics.KEY_SIMILARITY, similarity);
        scores.put(BuiltinRubrics.KEY_ANSWER_CORRECTNESS, answerCorrectness);
        scores.put(BuiltinRubrics.KEY_CONTEXT_PRECISION, contextPrecision);
        scores.put(BuiltinRubrics.KEY_CONTEXT_RECALL, contextRecall);
        scores.put(BuiltinRubrics.KEY_CONTEXT_RELEVANCE, contextRelevance);
        if (tcm != null) {
            scores.put("toolSelection", tcm.selectionScore);
            scores.put("toolParam", tcm.paramScore);
        }
        if (adm != null) {
            scores.put("intent", adm.intentScore);
            scores.put("branch", adm.branchScore);
            scores.put("reasoning", adm.reasoningScore);
        }
        return scores;
    }

    /**
     * 综合分加权（v2 口径迁移，工单 0133 R1）：权重表来自 RubricService.resolveWeights(evalType)，
     * 内置种子与旧硬编码 switch 同口径（对照测试 EvalExecutionServiceWeightTest 保证一致）：
     * <ul>
     *   <li>RAG_RETRIEVAL：检索质量（F1 0.4 + Top3 0.2 + MRR 0.2 + NDCG 0.2）</li>
     *   <li>ANSWER_QUALITY：答案质量（忠实 0.25 + 相关 0.25 + 完整 0.15 + 相似 0.05 + 正确性 0.2 + 幻觉惩罚 0.1）</li>
     *   <li>CONTEXT_QUALITY：上下文维度（精确率 0.4 + 召回率 0.4 + 相关性 0.2）</li>
     *   <li>TOOL_CALL：工具调用质量（工具选择 0.5 + 参数正确 0.5）</li>
     *   <li>AGENT_DECISION：决策质量（意图 0.3 + 分支 0.3 + 推理 0.4）</li>
     *   <li>默认：检索 0.4 + 上下文 0.2 + 质量 0.4（平铺展开）</li>
     * </ul>
     * 注：权重版本随结果写入 eval_detail.weightVersion（v2-rubric）；权重表缺失的指标 key 按 0 分计。
     */
    double computeOverall(Map<String, Double> weights, Map<String, Double> metricScores) {
        double overall = 0.0;
        for (Map.Entry<String, Double> w : weights.entrySet()) {
            Double score = metricScores.get(w.getKey());
            overall += (score == null ? 0.0 : score) * w.getValue();
        }
        return clamp(overall);
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

    /** 单条样本执行产物（结果 + unknown 统计） */
    private record EvalSample(EvalResultEntity result, int unknownDims, int judgeDims) {}

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
