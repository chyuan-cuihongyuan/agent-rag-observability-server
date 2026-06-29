package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.EvalTaskMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.EvalTaskPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class EvalTaskRepository implements IEvalTaskRepository {

    @Resource
    private EvalTaskMapper evalTaskMapper;

    @Override
    public void save(EvalTaskEntity entity) {
        evalTaskMapper.insert(toPO(entity));
    }

    @Override
    public EvalTaskEntity queryByTaskId(String taskId) {
        EvalTaskPO po = evalTaskMapper.selectByTaskId(taskId);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<EvalTaskEntity> queryList(int page, int size) {
        return evalTaskMapper.selectList((page - 1) * size, size).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<EvalTaskEntity> queryRecentCompleted(int limit) {
        return evalTaskMapper.selectRecentCompleted(limit).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public void updateStatus(String taskId, String status) {
        evalTaskMapper.updateStatus(taskId, status);
    }

    @Override
    public void updateProgress(String taskId, int completedCount, Double avgScore) {
        evalTaskMapper.updateProgress(taskId, completedCount, avgScore);
    }

    private EvalTaskPO toPO(EvalTaskEntity e) {
        return EvalTaskPO.builder().taskId(e.getTaskId()).taskName(e.getTaskName()).evalType(e.getEvalType()).datasetId(e.getDatasetId()).status(e.getStatus()).totalCount(e.getTotalCount()).completedCount(e.getCompletedCount()).modelVersion(e.getModelVersion()).ragStrategyVersion(e.getRagStrategyVersion()).avgOverallScore(e.getAvgOverallScore()).createTime(e.getCreateTime()).updateTime(e.getUpdateTime()).build();
    }

    private EvalTaskEntity toEntity(EvalTaskPO p) {
        return EvalTaskEntity.builder().id(p.getId()).taskId(p.getTaskId()).taskName(p.getTaskName()).evalType(p.getEvalType()).datasetId(p.getDatasetId()).status(p.getStatus()).totalCount(p.getTotalCount()).completedCount(p.getCompletedCount()).modelVersion(p.getModelVersion()).ragStrategyVersion(p.getRagStrategyVersion()).avgOverallScore(p.getAvgOverallScore()).createTime(p.getCreateTime()).updateTime(p.getUpdateTime()).build();
    }
}
