package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.EvalDatasetMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.EvalDatasetPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class EvalDatasetRepository implements IEvalDatasetRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private EvalDatasetMapper evalDatasetMapper;

    @Override
    public void save(EvalDatasetEntity entity) {
        if (entity.getDatasetId() == null || entity.getDatasetId().isEmpty()) {
            entity.setDatasetId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }
        String now = LocalDateTime.now().format(FMT);
        EvalDatasetPO po = EvalDatasetPO.builder()
                .datasetId(entity.getDatasetId()).datasetName(entity.getDatasetName())
                .description(entity.getDescription()).itemCount(entity.getItemCount())
                .itemsJson(entity.getItemsJson()).createTime(now).updateTime(now)
                .version(entity.getVersion() == null ? 1 : entity.getVersion())
                .pool(entity.getPool()).source(entity.getSource())
                .frozen(Boolean.TRUE.equals(entity.getFrozen()) ? 1 : 0)
                .build();
        evalDatasetMapper.insert(po);
    }

    @Override
    public EvalDatasetEntity queryByDatasetId(String datasetId) {
        EvalDatasetPO po = evalDatasetMapper.selectByDatasetId(datasetId);
        return toEntity(po);
    }

    @Override
    public List<EvalDatasetEntity> queryList(int page, int size) {
        return evalDatasetMapper.selectList((page - 1) * size, size).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    // ========== 新增：版本化 + 三池 + 冻结（工单 0134 R2） ==========

    @Override
    public boolean update(EvalDatasetEntity entity) {
        EvalDatasetPO po = EvalDatasetPO.builder()
                .datasetId(entity.getDatasetId())
                .description(entity.getDescription()).itemCount(entity.getItemCount())
                .itemsJson(entity.getItemsJson())
                .pool(entity.getPool()).source(entity.getSource())
                .updateTime(LocalDateTime.now().format(FMT))
                .build();
        return evalDatasetMapper.update(po) > 0;
    }

    @Override
    public List<EvalDatasetEntity> queryByPool(String pool, int page, int size) {
        return evalDatasetMapper.selectByPool(pool, (page - 1) * size, size).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvalDatasetEntity> queryVersions(String datasetName) {
        return evalDatasetMapper.selectVersions(datasetName).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public int maxVersion(String datasetName) {
        Integer max = evalDatasetMapper.selectMaxVersion(datasetName);
        return max == null ? 0 : max;
    }

    @Override
    public boolean updateFrozen(String datasetId, boolean frozen) {
        return evalDatasetMapper.updateFrozen(datasetId, frozen ? 1 : 0,
                LocalDateTime.now().format(FMT)) > 0;
    }

    private EvalDatasetEntity toEntity(EvalDatasetPO po) {
        if (po == null) {
            return null;
        }
        return EvalDatasetEntity.builder().datasetId(po.getDatasetId()).datasetName(po.getDatasetName())
                .description(po.getDescription()).itemCount(po.getItemCount()).itemsJson(po.getItemsJson())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime())
                .version(po.getVersion()).pool(po.getPool()).source(po.getSource())
                .frozen(po.getFrozen() != null && po.getFrozen() == 1)
                .build();
    }
}
