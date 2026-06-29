package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.EvalTaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EvalTaskMapper {
    void insert(EvalTaskPO po);
    EvalTaskPO selectByTaskId(@Param("taskId") String taskId);
    List<EvalTaskPO> selectList(@Param("offset") int offset, @Param("size") int size);
    List<EvalTaskPO> selectRecentCompleted(@Param("limit") int limit);
    void updateStatus(@Param("taskId") String taskId, @Param("status") String status);
    void updateProgress(@Param("taskId") String taskId, @Param("completedCount") int completedCount, @Param("avgOverallScore") Double avgOverallScore);
}
