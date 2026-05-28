package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.ChatResultLogPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatResultLogMapper {
    void insert(ChatResultLogPO po);
}
