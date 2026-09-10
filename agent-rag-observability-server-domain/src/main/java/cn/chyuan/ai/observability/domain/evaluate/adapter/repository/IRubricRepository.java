package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.RubricEntity;

import java.util.List;

/**
 * Rubric 仓储端口（工单 0133 R1）。
 */
public interface IRubricRepository {

    void insert(RubricEntity entity);

    RubricEntity queryByRubricId(String rubricId);

    RubricEntity queryByName(String name);

    /** 按评测类型取启用中的 Rubric（用户自建优先于内置，版本高者优先） */
    RubricEntity queryEnabledByEvalType(String evalType);

    List<RubricEntity> queryList(int page, int size);

    /** 按 rubricId 更新可变字段（name/evalType/version/dimensions/enabled） */
    boolean update(RubricEntity entity);

    /**
     * 内置种子幂等插入：name 已存在则跳过返回 false，插入成功返回 true。
     * 并发冲突（唯一键）按已存在处理。
     */
    boolean seedBuiltinIfAbsent(RubricEntity entity);
}
