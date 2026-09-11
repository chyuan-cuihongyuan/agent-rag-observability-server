package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.cost.adapter.repository.IModelPricingRepository;
import cn.chyuan.ai.observability.domain.cost.model.entity.ModelPricingEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.ModelPricingMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.ModelPricingPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型计价仓储实现（工单 0148 U2）— PO/实体互转；模型名精确匹配（读侧不区分大小写归一）。
 */
@Slf4j
@Repository
public class ModelPricingRepository implements IModelPricingRepository {

    @Resource
    private ModelPricingMapper modelPricingMapper;

    @Override
    public ModelPricingEntity queryByModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        ModelPricingPO po = modelPricingMapper.selectByModel(model.trim());
        if (po == null) {
            // 兼容大小写差异的二次查找
            return modelPricingMapper.selectAll().stream()
                    .filter(p -> p.getModel() != null && p.getModel().equalsIgnoreCase(model.trim()))
                    .findFirst()
                    .map(this::toEntity)
                    .orElse(null);
        }
        return toEntity(po);
    }

    @Override
    public List<ModelPricingEntity> queryAll() {
        return modelPricingMapper.selectAll().stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public void save(ModelPricingEntity entity) {
        modelPricingMapper.upsert(toPo(entity));
    }

    private ModelPricingPO toPo(ModelPricingEntity e) {
        return ModelPricingPO.builder()
                .id(e.getId())
                .model(e.getModel())
                .inputPricePer1k(e.getInputPricePer1k())
                .outputPricePer1k(e.getOutputPricePer1k())
                .remark(e.getRemark())
                .updateTime(e.getUpdateTime())
                .build();
    }

    private ModelPricingEntity toEntity(ModelPricingPO po) {
        if (po == null) {
            return null;
        }
        return ModelPricingEntity.builder()
                .id(po.getId())
                .model(po.getModel())
                .inputPricePer1k(po.getInputPricePer1k())
                .outputPricePer1k(po.getOutputPricePer1k())
                .remark(po.getRemark())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
