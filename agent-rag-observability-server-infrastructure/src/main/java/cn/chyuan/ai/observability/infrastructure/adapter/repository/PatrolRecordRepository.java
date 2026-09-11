package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.patrol.adapter.repository.IPatrolRecordRepository;
import cn.chyuan.ai.observability.domain.patrol.model.entity.PatrolRecordEntity;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.PatrolRecordMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.PatrolRecordPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 巡检拨测记录仓储实现（工单 0137 S1）— PO/实体互转；status 枚举与落库字符串互转。
 */
@Slf4j
@Repository
public class PatrolRecordRepository implements IPatrolRecordRepository {

    @Resource
    private PatrolRecordMapper patrolRecordMapper;

    @Override
    public void insert(PatrolRecordEntity entity) {
        patrolRecordMapper.insert(toPo(entity));
    }

    @Override
    public List<PatrolRecordEntity> queryList(int page, int size) {
        return patrolRecordMapper.selectList((page - 1) * size, size).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public PatrolRecordEntity queryLatest() {
        return toEntity(patrolRecordMapper.selectLatest());
    }

    @Override
    public List<PatrolRecordEntity> queryByRoundId(String roundId) {
        return patrolRecordMapper.selectByRoundId(roundId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    private PatrolRecordPO toPo(PatrolRecordEntity e) {
        return PatrolRecordPO.builder()
                .id(e.getId())
                .roundId(e.getRoundId())
                .taskRef(e.getTaskRef())
                .query(e.getQuery())
                .agentId(e.getAgentId())
                .status(e.getStatus() == null ? null : e.getStatus().getCode())
                .score(e.getScore())
                .durationMs(e.getDurationMs())
                .errorSummary(e.getErrorSummary())
                .traceId(e.getTraceId())
                .createTime(e.getCreateTime())
                .build();
    }

    private PatrolRecordEntity toEntity(PatrolRecordPO po) {
        if (po == null) {
            return null;
        }
        return PatrolRecordEntity.builder()
                .id(po.getId())
                .roundId(po.getRoundId())
                .taskRef(po.getTaskRef())
                .query(po.getQuery())
                .agentId(po.getAgentId())
                .status(PatrolStatus.fromCode(po.getStatus()))
                .score(po.getScore())
                .durationMs(po.getDurationMs())
                .errorSummary(po.getErrorSummary())
                .traceId(po.getTraceId())
                .createTime(po.getCreateTime())
                .build();
    }
}
