package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.PairwiseRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pairwise 对局记录表 mapper（工单 0170 X1）：双方言通用语句。
 */
@Mapper
public interface PairwiseRecordMapper {

    void insert(PairwiseRecordPO po);

    List<PairwiseRecordPO> selectList(@Param("taskA") String taskA, @Param("taskB") String taskB,
                                      @Param("limit") int limit);
}
