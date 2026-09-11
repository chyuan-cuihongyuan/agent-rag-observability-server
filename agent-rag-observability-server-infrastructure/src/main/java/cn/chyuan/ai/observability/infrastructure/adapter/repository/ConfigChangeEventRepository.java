package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.alert.adapter.repository.IConfigChangeEventRepository;
import cn.chyuan.ai.observability.domain.alert.model.entity.ConfigChangeEventEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.ConfigChangeEventMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.ConfigChangeEventPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 配置变更事件仓储实现（工单 0182 Y6）。
 */
@Slf4j
@Repository
public class ConfigChangeEventRepository implements IConfigChangeEventRepository {

    @Resource
    private ConfigChangeEventMapper configChangeEventMapper;

    @Override
    public void insert(ConfigChangeEventEntity entity) {
        configChangeEventMapper.insert(ConfigChangeEventPO.builder()
                .tableName(entity.getTableName())
                .bizKey(entity.getBizKey())
                .changesJson(entity.getChangesJson())
                .operator(entity.getOperator())
                .createTime(entity.getCreateTime())
                .build());
    }

    @Override
    public List<ConfigChangeEventEntity> queryList(String tableName, String operator, int page, int size) {
        return configChangeEventMapper.selectList(
                        tableName == null || tableName.isBlank() ? null : tableName.trim(),
                        operator == null || operator.isBlank() ? null : operator.trim(),
                        (page - 1) * size, size).stream()
                .<ConfigChangeEventEntity>map(po -> ConfigChangeEventEntity.builder()
                        .id(po.getId())
                        .tableName(po.getTableName())
                        .bizKey(po.getBizKey())
                        .changesJson(po.getChangesJson())
                        .operator(po.getOperator())
                        .createTime(po.getCreateTime())
                        .build())
                .collect(Collectors.toList());
    }
}
