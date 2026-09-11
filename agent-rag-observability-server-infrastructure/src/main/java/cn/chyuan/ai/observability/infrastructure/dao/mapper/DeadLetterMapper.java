package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.DeadLetterPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 死信记录表 mapper（工单 0180 Y4）：双方言通用语句。
 */
@Mapper
public interface DeadLetterMapper {

    void insert(DeadLetterPO po);

    List<DeadLetterPO> selectList(@Param("status") String status,
                                  @Param("offset") int offset, @Param("size") int size);

    DeadLetterPO selectById(@Param("id") long id);

    int delete(@Param("id") long id);

    int markRetryFailed(@Param("id") long id, @Param("lastError") String lastError);
}
