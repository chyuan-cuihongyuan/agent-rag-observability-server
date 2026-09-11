package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.EvalResultPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EvalResultMapper {
    void insert(EvalResultPO po);
    void batchInsert(@Param("list") List<EvalResultPO> list);
    /** trial 为 null 时查全部 trial（兼容既有调用方），非空时按 trial_no 过滤（工单 0135 R3） */
    List<EvalResultPO> selectByTaskId(@Param("taskId") String taskId, @Param("trial") Integer trial,
                                      @Param("offset") int offset, @Param("size") int size);
    long countByTaskId(@Param("taskId") String taskId);

    /** 低分明细扫描（工单 0138 S2）：overall_score 非空且低于阈值，按时间降序 */
    List<EvalResultPO> selectLowScore(@Param("maxOverallScore") double maxOverallScore, @Param("limit") int limit);
}
