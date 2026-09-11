package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.DriftEventPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 漂移事件表 mapper（工单 0154 U8）。
 */
@Mapper
public interface DriftEventMapper {

    void insert(DriftEventPO po);

    List<DriftEventPO> selectList(@Param("offset") int offset, @Param("size") int size);
}
