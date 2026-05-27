package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.EvalResultPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EvalResultMapper {
    void insert(EvalResultPO po);
    void batchInsert(@Param("list") List<EvalResultPO> list);
    List<EvalResultPO> selectByTaskId(@Param("taskId") String taskId, @Param("offset") int offset, @Param("size") int size);
    long countByTaskId(@Param("taskId") String taskId);
}
