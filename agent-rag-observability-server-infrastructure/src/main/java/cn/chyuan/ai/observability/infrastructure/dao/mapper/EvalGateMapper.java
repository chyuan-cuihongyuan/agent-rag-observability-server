package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.EvalGatePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评测门禁规则表 mapper（工单 0136 R4）。
 */
@Mapper
public interface EvalGateMapper {
    void insert(EvalGatePO po);

    EvalGatePO selectByGateId(@Param("gateId") String gateId);

    EvalGatePO selectByName(@Param("name") String name);

    List<EvalGatePO> selectList(@Param("offset") int offset, @Param("size") int size);

    int update(EvalGatePO po);
}
