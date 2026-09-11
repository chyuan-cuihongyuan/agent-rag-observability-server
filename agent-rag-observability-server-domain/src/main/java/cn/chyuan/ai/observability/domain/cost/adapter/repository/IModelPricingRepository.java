package cn.chyuan.ai.observability.domain.cost.adapter.repository;

import cn.chyuan.ai.observability.domain.cost.model.entity.ModelPricingEntity;

import java.util.List;

/**
 * 模型计价仓储端口（工单 0148 U2）。
 */
public interface IModelPricingRepository {

    /** 按模型名精确取价（不区分大小写；未配置返回 null） */
    ModelPricingEntity queryByModel(String model);

    List<ModelPricingEntity> queryAll();

    /** 幂等保存（按 model 唯一键 upsert 语义） */
    void save(ModelPricingEntity entity);
}
