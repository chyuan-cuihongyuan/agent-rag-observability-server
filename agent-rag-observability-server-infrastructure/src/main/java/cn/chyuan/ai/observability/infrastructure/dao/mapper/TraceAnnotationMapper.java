package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.TraceAnnotationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 人工注解表 mapper（工单 0150 U4）：upsert 同 id 按 databaseId 成对分叉（见 XML）。
 */
@Mapper
public interface TraceAnnotationMapper {

    void upsert(TraceAnnotationPO po);

    TraceAnnotationPO selectByTraceAndOperator(@Param("traceId") String traceId,
                                               @Param("operator") String operator);

    List<TraceAnnotationPO> selectList(@Param("score") Integer score,
                                       @Param("offset") int offset, @Param("size") int size);
}
