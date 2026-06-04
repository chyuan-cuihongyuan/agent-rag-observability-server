package cn.chyuan.ai.observability.domain.observe.adapter.cache;

/**
 * 缓存端口接口 - domain 层定义，infrastructure 层实现
 * 用于解耦 domain 层对 Redis 的直接依赖
 */
public interface ICachePort {

    /**
     * 递增指定类型的计数器
     * @param type 数据类型（如 agent_decision / rag_retrieval / chat_result）
     */
    void increment(String type);
}
