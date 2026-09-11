package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.ModelPricingPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模型计价表 mapper（工单 0148 U2）。
 */
@Mapper
public interface ModelPricingMapper {

    /** 按 model 幂等 upsert（双方言通用：先删后插不适用——用 databaseId 分叉 ON DUPLICATE/ON CONFLICT） */
    void upsert(ModelPricingPO po);

    ModelPricingPO selectByModel(@Param("model") String model);

    List<ModelPricingPO> selectAll();
}
