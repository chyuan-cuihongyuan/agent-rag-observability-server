package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.MemoryRecallLogPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemoryRecallLogMapper {
    void insert(MemoryRecallLogPO po);
}
