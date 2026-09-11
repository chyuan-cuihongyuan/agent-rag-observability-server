package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.CaseCandidatePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Case 候选表 mapper（工单 0138 S2）。
 */
@Mapper
public interface CaseCandidateMapper {

    void insert(CaseCandidatePO po);

    CaseCandidatePO selectBySourceRef(@Param("source") String source, @Param("sourceRef") String sourceRef);

    List<CaseCandidatePO> selectList(@Param("source") String source, @Param("status") String status,
                                     @Param("offset") int offset, @Param("size") int size);

    List<CaseCandidatePO> selectByIds(@Param("ids") List<Long> ids);

    /** 仅 PENDING 生效的批量处置（返回受影响行数） */
    int updateStatus(@Param("ids") List<Long> ids, @Param("status") String status,
                     @Param("promotedDatasetId") String promotedDatasetId);

    /** 归因标注留痕（工单 0139 S3） */
    int updateAttribution(@Param("id") long id, @Param("attribution") String attribution,
                          @Param("note") String note, @Param("by") String by, @Param("at") String at);

    /** 已标注候选统计取数（时间窗/来源可空过滤；工单 0139 S3） */
    List<CaseCandidatePO> selectAttributed(@Param("startTime") String startTime, @Param("endTime") String endTime,
                                           @Param("source") String source, @Param("limit") int limit);
}
