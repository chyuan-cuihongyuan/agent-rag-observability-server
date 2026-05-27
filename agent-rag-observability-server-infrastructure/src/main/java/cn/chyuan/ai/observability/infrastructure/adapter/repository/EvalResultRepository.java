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
    public List<EvalResultEntity> queryByTaskId(String taskId, int page, int size) {
        return evalResultMapper.selectByTaskId(taskId, (page - 1) * size, size).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public long countByTaskId(String taskId) {
        return evalResultMapper.countByTaskId(taskId);
    }

    private EvalResultPO toPO(EvalResultEntity e) {
        return EvalResultPO.builder().taskId(e.getTaskId()).traceId(e.getTraceId()).queryText(e.getQueryText()).standardAnswer(e.getStandardAnswer()).actualAnswer(e.getActualAnswer()).recallScore(e.getRecallScore()).precisionScore(e.getPrecisionScore()).f1Score(e.getF1Score()).top3HitRate(e.getTop3HitRate()).answerSimilarity(e.getAnswerSimilarity()).faithfulnessScore(e.getFaithfulnessScore()).relevanceScore(e.getRelevanceScore()).hallucinationFlag(e.getHallucinationFlag()).completenessScore(e.getCompletenessScore()).overallScore(e.getOverallScore()).evalDetail(e.getEvalDetail()).createTime(e.getCreateTime()).build();
    }

    private EvalResultEntity toEntity(EvalResultPO p) {
        return EvalResultEntity.builder().id(p.getId()).taskId(p.getTaskId()).traceId(p.getTraceId()).queryText(p.getQueryText()).standardAnswer(p.getStandardAnswer()).actualAnswer(p.getActualAnswer()).recallScore(p.getRecallScore()).precisionScore(p.getPrecisionScore()).f1Score(p.getF1Score()).top3HitRate(p.getTop3HitRate()).answerSimilarity(p.getAnswerSimilarity()).faithfulnessScore(p.getFaithfulnessScore()).relevanceScore(p.getRelevanceScore()).hallucinationFlag(p.getHallucinationFlag()).completenessScore(p.getCompletenessScore()).overallScore(p.getOverallScore()).evalDetail(p.getEvalDetail()).createTime(p.getCreateTime()).build();
    }
}
