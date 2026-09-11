package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.PatrolRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 巡检拨测记录表 mapper（工单 0137 S1）。
 */
@Mapper
public interface PatrolRecordMapper {

    void insert(PatrolRecordPO po);

    List<PatrolRecordPO> selectList(@Param("offset") int offset, @Param("size") int size);

    PatrolRecordPO selectLatest();

    List<PatrolRecordPO> selectByRoundId(@Param("roundId") String roundId);

    /** 最近失败记录（status <> SUCCESS；工单 0138 S2 挖掘来源③） */
    List<PatrolRecordPO> selectLatestFailures(@Param("limit") int limit);
}
