package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.JudgeCachePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * judge 判定缓存表 mapper（工单 0176 X7）：upsert 同 id 按 databaseId 成对分叉（见 XML）。
 */
@Mapper
public interface JudgeCacheMapper {

    void upsert(JudgeCachePO po);

    JudgeCachePO selectByKey(@Param("cacheKey") String cacheKey);

    int incrementHit(@Param("cacheKey") String cacheKey);

    int clearByRubric(@Param("rubricId") String rubricId);
}
