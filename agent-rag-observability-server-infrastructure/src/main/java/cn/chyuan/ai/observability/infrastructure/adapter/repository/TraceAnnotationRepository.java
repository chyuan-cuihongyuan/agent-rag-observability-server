package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.insight.adapter.repository.ITraceAnnotationRepository;
import cn.chyuan.ai.observability.domain.insight.model.entity.TraceAnnotationEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.TraceAnnotationMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.TraceAnnotationPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 人工注解仓储实现（工单 0150 U4）— PO/实体互转；upsert 语句由 MyBatis databaseId 路由。
 */
@Slf4j
@Repository
public class TraceAnnotationRepository implements ITraceAnnotationRepository {

    @Resource
    private TraceAnnotationMapper traceAnnotationMapper;

    @Override
    public void upsert(TraceAnnotationEntity entity) {
        traceAnnotationMapper.upsert(toPo(entity));
    }

    @Override
    public TraceAnnotationEntity queryByTraceAndOperator(String traceId, String operator) {
        return toEntity(traceAnnotationMapper.selectByTraceAndOperator(traceId, operator));
    }

    @Override
    public List<TraceAnnotationEntity> queryList(Integer score, int page, int size) {
        return traceAnnotationMapper.selectList(score, (page - 1) * size, size).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    private TraceAnnotationPO toPo(TraceAnnotationEntity e) {
        return TraceAnnotationPO.builder()
                .id(e.getId())
                .traceId(e.getTraceId())
                .score(e.getScore())
                .note(e.getNote())
                .operator(e.getOperator())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }

    private TraceAnnotationEntity toEntity(TraceAnnotationPO po) {
        if (po == null) {
            return null;
        }
        return TraceAnnotationEntity.builder()
                .id(po.getId())
                .traceId(po.getTraceId())
                .score(po.getScore())
                .note(po.getNote())
                .operator(po.getOperator())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
