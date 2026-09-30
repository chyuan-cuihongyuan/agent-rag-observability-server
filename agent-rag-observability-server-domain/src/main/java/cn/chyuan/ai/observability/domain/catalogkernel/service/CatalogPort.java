package cn.chyuan.ai.observability.domain.catalogkernel.service;

import java.util.List;

/**
 * 服务目录端口（工单 1097 ES8，consul 思想）。
 * register·watch·lock 入口统一编排：两级目录/健康聚合/阻塞查询/prepared query/
 * session TTL/锁与信号量/反熵收敛组合管线；tracekernel 服务拓扑形状只读联动
 * （形状键 service·name·traceId·spanId 对齐，不 import tracekernel）/
 * catalog-kernel.enabled 默认关（开启才改变行为）。
 */
public interface CatalogPort {

    // —— 目录与健康（ES1/ES2）——
    boolean register(String node, String service);

    int deregisterNode(String node);

    void reportCheck(String node, String service, String checkId, String status);

    String health(String service);

    List<String> nodes(String service);

    // —— 阻塞查询（ES3）——
    long watch(String waiterId, long sinceIndex);

    List<String> notifyChange();

    String takeWaiter(String waiterId);

    long index();

    // —— prepared query（ES4）——
    void prepare(String name, String service, String tagFilter);

    List<String> executeQuery(String name, String failingNodes);

    // —— session 与锁（ES5/ES6）——
    String session(String node, long ttlTicks);

    void renew(String sessionId);

    void invalidate(String sessionId);

    long tick();

    String lock(String key, String sessionId);

    String semaphore(String key, String sessionId, int limit);

    String lockHolder(String key);

    // —— 反熵收敛（ES7）——
    List<String> converge(List<String> memberPairs);

    // —— tracekernel 形状只读联动（ES8）——
    List<String> topologyShape();

    static CatalogPort inMemory(long startTick) {
        return new CatalogHub(startTick);
    }
}
