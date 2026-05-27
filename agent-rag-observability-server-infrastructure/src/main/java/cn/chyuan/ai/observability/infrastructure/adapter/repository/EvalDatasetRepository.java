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
                .itemsJson(entity.getItemsJson()).createTime(now).updateTime(now).build();
        evalDatasetMapper.insert(po);
    }

    @Override
    public EvalDatasetEntity queryByDatasetId(String datasetId) {
        EvalDatasetPO po = evalDatasetMapper.selectByDatasetId(datasetId);
        if (po == null) return null;
        return EvalDatasetEntity.builder().datasetId(po.getDatasetId()).datasetName(po.getDatasetName())
                .description(po.getDescription()).itemCount(po.getItemCount()).itemsJson(po.getItemsJson())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime()).build();
    }

    @Override
    public List<EvalDatasetEntity> queryList(int page, int size) {
        return evalDatasetMapper.selectList((page - 1) * size, size).stream()
                .map(p -> EvalDatasetEntity.builder().datasetId(p.getDatasetId()).datasetName(p.getDatasetName())
                        .description(p.getDescription()).itemCount(p.getItemCount())
                        .createTime(p.getCreateTime()).updateTime(p.getUpdateTime()).build())
                .collect(Collectors.toList());
    }
}
