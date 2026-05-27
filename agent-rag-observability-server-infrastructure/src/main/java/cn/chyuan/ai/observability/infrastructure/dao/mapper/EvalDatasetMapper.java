package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.EvalDatasetPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EvalDatasetMapper {
    void insert(EvalDatasetPO po);
    EvalDatasetPO selectByDatasetId(@Param("datasetId") String datasetId);
    List<EvalDatasetPO> selectList(@Param("offset") int offset, @Param("size") int size);
}
