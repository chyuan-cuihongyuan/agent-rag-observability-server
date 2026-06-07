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
        // 语义相似度优先用 LLM 评判值，降级用词面相似度
        double similarity = (verdict != null && verdict.getSimilarity() > 0)
                ? verdict.getSimilarity() : rm.getAnswerSimilarity();
        int hallucinationFlag = hallucinationRate >= 0.3 ? 1 : 0;

        // 3. 按评测类型加权综合分
        double overall = computeOverall(evalType, rm, faithfulness, relevance, hallucinationRate,
                completeness, similarity);

        // 4. 组装明细
        JSONObject detail = new JSONObject();
        detail.put("evalType", evalType);
        detail.put("retrievalCount", actualChunks.size());
        detail.put("standardChunkCount", standardChunks.size());
        if (verdict != null) {
            detail.put("judgeDegraded", verdict.isDegraded());
            detail.put("judgeDetail", verdict.getDetail());
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
                .answerSimilarity(round(similarity))
                .faithfulnessScore(round(faithfulness))
                .relevanceScore(round(relevance))
                .hallucinationFlag(hallucinationFlag)
                .completenessScore(round(completeness))
                .overallScore(round(overall))
                .evalDetail(detail.toJSONString())
                .createTime(LocalDateTime.now().format(FMT))
                .build();
    }

    /**
     * 综合分加权策略，按评测类型侧重不同维度：
     * - RAG_RETRIEVAL：只看检索质量（F1 0.6 + Top3 0.4）
     * - ANSWER_QUALITY：侧重答案质量（忠实 0.3 + 相关 0.3 + 完整 0.2 + 相似 0.1 - 幻觉惩罚 0.1）
     * - AGENT_DECISION / 其它：检索与质量各半
     */
    private double computeOverall(String evalType, RetrievalMetrics rm, double faithfulness,
                                  double relevance, double hallucinationRate, double completeness,
                                  double similarity) {
        switch (evalType) {
            case "RAG_RETRIEVAL":
                return rm.getF1() * 0.6 + rm.getTop3HitRate() * 0.4;
            case "ANSWER_QUALITY":
                return clamp(faithfulness * 0.3 + relevance * 0.3 + completeness * 0.2
                        + similarity * 0.1 + (1 - hallucinationRate) * 0.1);
            default:
                double retrievalScore = rm.getF1() * 0.6 + rm.getTop3HitRate() * 0.4;
                double qualityScore = clamp(faithfulness * 0.4 + relevance * 0.4
                        + (1 - hallucinationRate) * 0.2);
                return retrievalScore * 0.5 + qualityScore * 0.5;
        }
    }

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
