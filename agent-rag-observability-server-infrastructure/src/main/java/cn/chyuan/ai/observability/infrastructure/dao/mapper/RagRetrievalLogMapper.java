package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.RagRetrievalLogPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RagRetrievalLogMapper {
    void insert(RagRetrievalLogPO po);
}
