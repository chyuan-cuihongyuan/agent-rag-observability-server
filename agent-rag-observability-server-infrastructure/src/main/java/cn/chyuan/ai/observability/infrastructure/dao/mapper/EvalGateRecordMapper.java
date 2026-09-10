package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.EvalGateRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评测门禁记录表 mapper（工单 0136 R4）。
 */
@Mapper
public interface EvalGateRecordMapper {
    void insert(EvalGateRecordPO po);

    /** 指定门禁最新一条判定记录（按 create_time/id 降序取一） */
    EvalGateRecordPO selectLatestByGateId(@Param("gateId") String gateId);

    /** 判定记录历史：gateId 为 null 时查全部（按 create_time 降序分页） */
    List<EvalGateRecordPO> selectList(@Param("gateId") String gateId,
                                      @Param("offset") int offset, @Param("size") int size);

    /** 按评测任务查判定记录（一任务一条） */
    EvalGateRecordPO selectByTaskId(@Param("taskId") String taskId);
}
