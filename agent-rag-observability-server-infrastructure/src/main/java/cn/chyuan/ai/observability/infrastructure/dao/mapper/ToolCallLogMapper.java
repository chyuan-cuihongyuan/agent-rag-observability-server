package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.ToolCallLogPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ToolCallLogMapper {
    void insert(ToolCallLogPO po);
}
