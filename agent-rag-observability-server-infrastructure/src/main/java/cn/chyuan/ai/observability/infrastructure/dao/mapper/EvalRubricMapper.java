package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.EvalRubricPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评测 Rubric 表 mapper（工单 0133 R1）。
 */
@Mapper
public interface EvalRubricMapper {
    void insert(EvalRubricPO po);

    EvalRubricPO selectByRubricId(@Param("rubricId") String rubricId);

    EvalRubricPO selectByName(@Param("name") String name);

    /** 按评测类型取启用中的 Rubric（用户自建优先于内置，版本高者优先，取一条） */
    EvalRubricPO selectEnabledByEvalType(@Param("evalType") String evalType);

    List<EvalRubricPO> selectList(@Param("offset") int offset, @Param("size") int size);

    int update(EvalRubricPO po);
}
