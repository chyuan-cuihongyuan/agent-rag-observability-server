package cn.chyuan.ai.observability.domain.evaluate.adapter.repository;

/**
 * judge 判定缓存仓储端口（工单 0176 X7）— 键=归一哈希（query+answer+rubric 版本），值=判定输出。
 */
public interface IJudgeCacheRepository {

    /** 按键取缓存输出（未命中/不可用返回 null） */
    String lookup(String cacheKey);

    /** 写入缓存（同键覆盖更新） */
    void insert(String cacheKey, String output, String rubricId);

    /** 命中计数 +1（幂等键存在时） */
    void incrementHit(String cacheKey);

    /** 按 rubric 版本失效清除，返回清除行数 */
    int clearByRubric(String rubricId);
}
