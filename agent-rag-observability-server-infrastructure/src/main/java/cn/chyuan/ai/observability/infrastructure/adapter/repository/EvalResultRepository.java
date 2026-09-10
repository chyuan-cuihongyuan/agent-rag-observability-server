package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.EvalResultMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.EvalResultPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class EvalResultRepository implements IEvalResultRepository {

    @Resource
    private EvalResultMapper evalResultMapper;

    @Override
    public void save(EvalResultEntity entity) {
        evalResultMapper.insert(toPO(entity));
    }

    @Override
    public void batchSave(List<EvalResultEntity> entities) {
        evalResultMapper.batchInsert(entities.stream().map(this::toPO).collect(Collectors.toList()));
    }

    @Override
    public List<EvalResultEntity> queryByTaskId(String taskId, Integer trial, int page, int size) {
        return evalResultMapper.selectByTaskId(taskId, trial, (page - 1) * size, size).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public long countByTaskId(String taskId) {
        return evalResultMapper.countByTaskId(taskId);
    }

    private EvalResultPO toPO(EvalResultEntity e) {
        return EvalResultPO.builder().taskId(e.getTaskId()).trialNo(e.getTrialNo()).traceId(e.getTraceId()).queryText(e.getQueryText()).standardAnswer(e.getStandardAnswer()).actualAnswer(e.getActualAnswer()).recallScore(e.getRecallScore()).precisionScore(e.getPrecisionScore()).f1Score(e.getF1Score()).top3HitRate(e.getTop3HitRate()).mrrScore(e.getMrrScore()).ndcgScore(e.getNdcgScore()).mapScore(e.getMapScore()).answerSimilarity(e.getAnswerSimilarity()).contextPrecision(e.getContextPrecision()).contextRecall(e.getContextRecall()).contextRelevance(e.getContextRelevance()).faithfulnessScore(e.getFaithfulnessScore()).relevanceScore(e.getRelevanceScore()).hallucinationFlag(e.getHallucinationFlag()).completenessScore(e.getCompletenessScore()).answerCorrectness(e.getAnswerCorrectness()).overallScore(e.getOverallScore()).evalDetail(e.getEvalDetail()).createTime(e.getCreateTime()).toolSelectionScore(e.getToolSelectionScore()).toolParamScore(e.getToolParamScore()).toolCallScore(e.getToolCallScore()).intentScore(e.getIntentScore()).branchScore(e.getBranchScore()).reasoningScore(e.getReasoningScore()).agentDecisionScore(e.getAgentDecisionScore()).build();
    }

    private EvalResultEntity toEntity(EvalResultPO p) {
        return EvalResultEntity.builder().id(p.getId()).taskId(p.getTaskId()).trialNo(p.getTrialNo()).traceId(p.getTraceId()).queryText(p.getQueryText()).standardAnswer(p.getStandardAnswer()).actualAnswer(p.getActualAnswer()).recallScore(p.getRecallScore()).precisionScore(p.getPrecisionScore()).f1Score(p.getF1Score()).top3HitRate(p.getTop3HitRate()).mrrScore(p.getMrrScore()).ndcgScore(p.getNdcgScore()).mapScore(p.getMapScore()).answerSimilarity(p.getAnswerSimilarity()).contextPrecision(p.getContextPrecision()).contextRecall(p.getContextRecall()).contextRelevance(p.getContextRelevance()).faithfulnessScore(p.getFaithfulnessScore()).relevanceScore(p.getRelevanceScore()).hallucinationFlag(p.getHallucinationFlag()).completenessScore(p.getCompletenessScore()).answerCorrectness(p.getAnswerCorrectness()).overallScore(p.getOverallScore()).evalDetail(p.getEvalDetail()).createTime(p.getCreateTime()).toolSelectionScore(p.getToolSelectionScore()).toolParamScore(p.getToolParamScore()).toolCallScore(p.getToolCallScore()).intentScore(p.getIntentScore()).branchScore(p.getBranchScore()).reasoningScore(p.getReasoningScore()).agentDecisionScore(p.getAgentDecisionScore()).build();
    }
}
