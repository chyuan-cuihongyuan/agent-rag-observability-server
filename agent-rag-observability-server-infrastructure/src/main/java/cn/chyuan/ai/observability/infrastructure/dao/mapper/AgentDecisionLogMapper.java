package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.AgentDecisionLogPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentDecisionLogMapper {
    void insert(AgentDecisionLogPO po);
}
