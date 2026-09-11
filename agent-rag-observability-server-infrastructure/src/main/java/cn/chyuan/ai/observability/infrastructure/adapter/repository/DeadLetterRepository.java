package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.dlq.adapter.repository.IDeadLetterRepository;
import cn.chyuan.ai.observability.domain.dlq.model.entity.DeadLetterEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.DeadLetterMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.DeadLetterPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 死信记录仓储实现（工单 0180 Y4）。
 */
@Slf4j
@Repository
public class DeadLetterRepository implements IDeadLetterRepository {

    @Resource
    private DeadLetterMapper deadLetterMapper;

    @Override
    public void insert(DeadLetterEntity entity) {
        deadLetterMapper.insert(toPo(entity));
    }

    @Override
    public List<DeadLetterEntity> queryList(String status, int page, int size) {
        return deadLetterMapper.selectList(status, (page - 1) * size, size).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public DeadLetterEntity queryById(long id) {
        return toEntity(deadLetterMapper.selectById(id));
    }

    @Override
    public boolean delete(long id) {
        return deadLetterMapper.delete(id) > 0;
    }

    @Override
    public boolean markRetryFailed(long id, String lastError) {
        return deadLetterMapper.markRetryFailed(id, lastError) > 0;
    }

    private DeadLetterPO toPo(DeadLetterEntity e) {
        return DeadLetterPO.builder()
                .id(e.getId())
                .topicTag(e.getTopicTag())
                .payload(e.getPayload())
                .retryCount(e.getRetryCount())
                .lastError(e.getLastError())
                .status(e.getStatus())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }

    private DeadLetterEntity toEntity(DeadLetterPO po) {
        if (po == null) {
            return null;
        }
        return DeadLetterEntity.builder()
                .id(po.getId())
                .topicTag(po.getTopicTag())
                .payload(po.getPayload())
                .retryCount(po.getRetryCount())
                .lastError(po.getLastError())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
