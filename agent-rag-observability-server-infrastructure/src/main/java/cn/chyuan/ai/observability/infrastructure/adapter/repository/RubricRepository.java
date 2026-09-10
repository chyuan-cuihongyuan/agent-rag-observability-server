package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IRubricRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.RubricEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricDimension;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.EvalRubricMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.EvalRubricPO;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rubric 仓储实现（工单 0133 R1）— PO/实体互转 + 内置种子幂等插入。
 */
@Slf4j
@Repository
public class RubricRepository implements IRubricRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private EvalRubricMapper evalRubricMapper;

    @Override
    public void insert(RubricEntity entity) {
        evalRubricMapper.insert(toPo(entity));
    }

    @Override
    public RubricEntity queryByRubricId(String rubricId) {
        return toEntity(evalRubricMapper.selectByRubricId(rubricId));
    }

    @Override
    public RubricEntity queryByName(String name) {
        return toEntity(evalRubricMapper.selectByName(name));
    }

    @Override
    public RubricEntity queryEnabledByEvalType(String evalType) {
        return toEntity(evalRubricMapper.selectEnabledByEvalType(evalType));
    }

    @Override
    public List<RubricEntity> queryList(int page, int size) {
        return evalRubricMapper.selectList((page - 1) * size, size).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public boolean update(RubricEntity entity) {
        entity.setUpdateTime(LocalDateTime.now().format(FMT));
        return evalRubricMapper.update(toPo(entity)) > 0;
    }

    @Override
    public boolean seedBuiltinIfAbsent(RubricEntity entity) {
        if (evalRubricMapper.selectByName(entity.getName()) != null) {
            return false;
        }
        try {
            evalRubricMapper.insert(toPo(entity));
            return true;
        } catch (DuplicateKeyException e) {
            // 并发种子冲突按已存在处理（幂等）
            return false;
        }
    }

    private EvalRubricPO toPo(RubricEntity entity) {
        String dimsJson = entity.getDimensionsJson() != null ? entity.getDimensionsJson()
                : (entity.getDimensions() == null ? null : JSON.toJSONString(entity.getDimensions()));
        return EvalRubricPO.builder()
                .id(entity.getId())
                .rubricId(entity.getRubricId())
                .name(entity.getName())
                .evalType(entity.getEvalType())
                .version(entity.getVersion() == null ? 1 : entity.getVersion())
                .dimensions(dimsJson)
                .enabled(Boolean.FALSE.equals(entity.getEnabled()) ? 0 : 1)
                .builtin(Boolean.TRUE.equals(entity.getBuiltin()) ? 1 : 0)
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private RubricEntity toEntity(EvalRubricPO po) {
        if (po == null) {
            return null;
        }
        List<RubricDimension> dims = null;
        if (po.getDimensions() != null && !po.getDimensions().isEmpty()) {
            try {
                dims = JSON.parseArray(po.getDimensions(), RubricDimension.class);
            } catch (Exception e) {
                log.warn("解析 Rubric dimensions 失败, rubricId={}: {}", po.getRubricId(), e.getMessage());
            }
        }
        return RubricEntity.builder()
                .id(po.getId())
                .rubricId(po.getRubricId())
                .name(po.getName())
                .evalType(po.getEvalType())
                .version(po.getVersion())
                .dimensionsJson(po.getDimensions())
                .dimensions(dims)
                .enabled(po.getEnabled() != null && po.getEnabled() == 1)
                .builtin(po.getBuiltin() != null && po.getBuiltin() == 1)
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
