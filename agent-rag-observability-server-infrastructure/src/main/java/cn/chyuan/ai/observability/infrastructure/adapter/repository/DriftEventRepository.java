package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.insight.adapter.repository.IDriftEventRepository;
import cn.chyuan.ai.observability.domain.insight.model.entity.DriftEventEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.DriftEventMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.DriftEventPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 漂移事件仓储实现（工单 0154 U8）。
 */
@Slf4j
@Repository
public class DriftEventRepository implements IDriftEventRepository {

    @Resource
    private DriftEventMapper driftEventMapper;

    @Override
    public void save(DriftEventEntity entity) {
        driftEventMapper.insert(DriftEventPO.builder()
                .metric(entity.getMetric())
                .currentValue(entity.getCurrentValue())
                .previousValue(entity.getPreviousValue())
                .threshold(entity.getThreshold())
                .detail(entity.getDetail())
                .createTime(entity.getCreateTime())
                .build());
    }

    @Override
    public List<DriftEventEntity> queryList(int page, int size) {
        return driftEventMapper.selectList((page - 1) * size, size).stream()
                .<DriftEventEntity>map(po -> DriftEventEntity.builder()
                        .id(po.getId())
                        .metric(po.getMetric())
                        .currentValue(po.getCurrentValue())
                        .previousValue(po.getPreviousValue())
                        .threshold(po.getThreshold())
                        .detail(po.getDetail())
                        .createTime(po.getCreateTime())
                        .build())
                .collect(Collectors.toList());
    }
}
