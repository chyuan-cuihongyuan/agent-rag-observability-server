package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.ConfigChangeEventPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 配置变更事件表 mapper（工单 0182 Y6）：双方言通用语句。
 */
@Mapper
public interface ConfigChangeEventMapper {

    void insert(ConfigChangeEventPO po);

    List<ConfigChangeEventPO> selectList(@Param("tableName") String tableName,
                                         @Param("operator") String operator,
                                         @Param("offset") int offset, @Param("size") int size);
}
