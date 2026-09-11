package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.alert.adapter.repository.IAlertSilenceRepository;
import cn.chyuan.ai.observability.domain.alert.model.entity.AlertSilenceEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.AlertSilenceMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.AlertSilencePO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 告警静默仓储实现（工单 0179 Y3）。
 */
@Slf4j
@Repository
public class AlertSilenceRepository implements IAlertSilenceRepository {

    @Resource
    private AlertSilenceMapper alertSilenceMapper;

    @Override
    public void insert(AlertSilenceEntity entity) {
        alertSilenceMapper.insert(AlertSilencePO.builder()
                .silenceKey(entity.getSilenceKey())
                .startsAt(entity.getStartsAt())
                .endsAt(entity.getEndsAt())
                .createdBy(entity.getCreatedBy())
                .reason(entity.getReason())
                .createTime(entity.getCreateTime())
                .build());
    }

    @Override
    public List<AlertSilenceEntity> queryAll() {
        return alertSilenceMapper.selectAll().stream()
                .<AlertSilenceEntity>map(po -> AlertSilenceEntity.builder()
                        .id(po.getId())
                        .silenceKey(po.getSilenceKey())
                        .startsAt(po.getStartsAt())
                        .endsAt(po.getEndsAt())
                        .createdBy(po.getCreatedBy())
                        .reason(po.getReason())
                        .createTime(po.getCreateTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteById(long id) {
        return alertSilenceMapper.deleteById(id) > 0;
    }
}
