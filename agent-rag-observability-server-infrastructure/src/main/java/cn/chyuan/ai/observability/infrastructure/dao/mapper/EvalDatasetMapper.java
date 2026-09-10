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

    // ========== 新增：版本化 + 三池 + 冻结（工单 0134 R2） ==========

    /** 更新可变字段（description/item_count/items_json/pool/source/update_time） */
    int update(EvalDatasetPO po);

    /** 按样本池筛选（pool 为 null 时查未分类：pool IS NULL，存量兼容） */
    List<EvalDatasetPO> selectByPool(@Param("pool") String pool, @Param("offset") int offset, @Param("size") int size);

    /** 同名数据集全部版本（版本号降序） */
    List<EvalDatasetPO> selectVersions(@Param("datasetName") String datasetName);

    /** 同名数据集最大版本号（无同名返回 null，仓储层归 0） */
    Integer selectMaxVersion(@Param("datasetName") String datasetName);

    /** 冻结/解冻（frozen 1/0） */
    int updateFrozen(@Param("datasetId") String datasetId, @Param("frozen") int frozen,
                     @Param("updateTime") String updateTime);
}
