package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmPairwisePort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IPairwiseRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.PairwiseOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * pairwise 对比判定服务（工单 0170 X1，借鉴 promptfoo/Chatbot Arena）—
 * 两任务按数据集条目对齐逐题 A/B 判定，落 eval_pairwise_record；
 * LLM 判定异常回退规则兜底（overallScore 高者胜/相等 TIE）。
 */
@Slf4j
@Service
public class PairwiseJudgeService {

    private final IEvalTaskRepository evalTaskRepository;
    private final IEvalResultRepository evalResultRepository;
    private final IEvalDatasetRepository evalDatasetRepository;
    private final ILlmPairwisePort llmPairwisePort;
    private final IPairwiseRecordRepository pairwiseRecordRepository;

    public PairwiseJudgeService(IEvalTaskRepository evalTaskRepository,
                                IEvalResultRepository evalResultRepository,
                                IEvalDatasetRepository evalDatasetRepository,
                                ILlmPairwisePort llmPairwisePort,
                                IPairwiseRecordRepository pairwiseRecordRepository) {
        this.evalTaskRepository = evalTaskRepository;
        this.evalResultRepository = evalResultRepository;
        this.evalDatasetRepository = evalDatasetRepository;
        this.llmPairwisePort = llmPairwisePort;
        this.pairwiseRecordRepository = pairwiseRecordRepository;
    }

    /**
     * 规则兜底判定（纯函数）：overallScore 高者胜，相等或任一为 null → TIE。
     */
    public static PairwiseOutcome ruleFallback(Double scoreA, Double scoreB) {
        if (scoreA == null || scoreB == null || Double.compare(scoreA, scoreB) == 0) {
            return PairwiseOutcome.TIE;
        }
        return scoreA > scoreB ? PairwiseOutcome.A_WIN : PairwiseOutcome.B_WIN;
    }

    /**
     * 执行一轮对比：取两任务在指定数据集上的第 1 次 trial 明细，按 queryText 对齐逐题判定。
     *
     * @return {taskId, modelVersion, winRate, tieRate, pairs, aWins, bWins, ties}
     */
    public Map<String, Object> compare(String taskA, String taskB, String datasetId) {
        requireTask(taskA);
        requireTask(taskB);
        List<EvalResultEntity> resultsA = evalResultRepository.queryByTaskId(taskA, 1, 1, 500);
        List<EvalResultEntity> resultsB = evalResultRepository.queryByTaskId(taskB, 1, 1, 500);
        Map<String, EvalResultEntity> byQueryB = new LinkedHashMap<>();
        for (EvalResultEntity r : resultsB) {
            if (r.getQueryText() != null) {
                byQueryB.putIfAbsent(r.getQueryText(), r);
            }
        }

        int aWins = 0;
        int bWins = 0;
        int ties = 0;
        int pairs = 0;
        for (EvalResultEntity ra : resultsA) {
            EvalResultEntity rb = byQueryB.get(ra.getQueryText());
            if (rb == null) {
                continue;
            }
            pairs++;
            PairwiseOutcome outcome;
            try {
                outcome = llmPairwisePort.judge(ra.getQueryText(), ra.getActualAnswer(), rb.getActualAnswer());
                if (outcome == null) {
                    outcome = ruleFallback(ra.getOverallScore(), rb.getOverallScore());
                }
            } catch (Exception e) {
                log.warn("LLM 对比判定异常，回退规则兜底: {}", e.getMessage());
                outcome = ruleFallback(ra.getOverallScore(), rb.getOverallScore());
            }
            switch (outcome) {
                case A_WIN -> aWins++;
                case B_WIN -> bWins++;
                case TIE -> ties++;
            }
            pairwiseRecordRepository.insert(taskA, taskB, datasetId, ra.getQueryText(),
                    outcome.getCode(), pairs);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskA", taskA);
        summary.put("taskB", taskB);
        summary.put("datasetId", datasetId);
        summary.put("pairs", pairs);
        summary.put("aWins", aWins);
        summary.put("bWins", bWins);
        summary.put("ties", ties);
        summary.put("winRateA", pairs == 0 ? 0.0 : round4((double) aWins / pairs));
        summary.put("winRateB", pairs == 0 ? 0.0 : round4((double) bWins / pairs));
        summary.put("tieRate", pairs == 0 ? 0.0 : round4((double) ties / pairs));
        return summary;
    }

    private void requireTask(String taskId) {
        EvalTaskEntity task = evalTaskRepository.queryByTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("评测任务不存在: " + taskId);
        }
    }

    private double round4(double v) {
        return Math.round(v * 10000d) / 10000d;
    }
}
