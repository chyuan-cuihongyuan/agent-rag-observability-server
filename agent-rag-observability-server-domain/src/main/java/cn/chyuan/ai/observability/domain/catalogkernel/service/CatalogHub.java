package cn.chyuan.ai.observability.domain.catalogkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务目录编排实现（工单 1097 ES8，consul 思想）。
 * 目录变更驱动阻塞查询唤醒；prepared query 按健康就近故障转移；
 * session 失效联动锁与信号量释放；反熵收敛不触发 watch。
 */
public final class CatalogHub implements CatalogPort {

    private final Catalogs catalog = new Catalogs();
    private final HealthChecks checks = new HealthChecks();
    private final BlockingQueries blocking =
            new BlockingQueries(() -> new ArrayList<>(catalog.snapshot()));
    private final PreparedQueries prepared = new PreparedQueries();
    private final Sessions sessions = new Sessions();
    private final Locks locks = new Locks();
    private final Map<String, List<String>> nodeTags = new LinkedHashMap<>();
    private long clock;

    public CatalogHub(long startTick) {
        this.clock = startTick;
    }

    @Override
    public boolean register(String node, String service) {
        boolean changed = catalog.register(node, service);
        if (changed) {
            blocking.change();
        }
        return changed;
    }

    @Override
    public int deregisterNode(String node) {
        checks.markNodeDown(node);
        int removed = catalog.deregisterNode(node);
        if (removed > 0) {
            blocking.change();
        }
        return removed;
    }

    @Override
    public void reportCheck(String node, String service, String checkId, String status) {
        checks.report(node, service, checkId, HealthChecks.Status.valueOf(status));
    }

    @Override
    public String health(String service) {
        return checks.aggregate(catalog.services(service), service).name();
    }

    @Override
    public List<String> nodes(String service) {
        return catalog.services(service);
    }

    @Override
    public long watch(String waiterId, long sinceIndex) {
        String snapshot = blocking.poll(waiterId, sinceIndex);
        return snapshot == null ? -1 : blocking.index();
    }

    @Override
    public List<String> notifyChange() {
        return blocking.change();
    }

    @Override
    public String takeWaiter(String waiterId) {
        return blocking.take(waiterId);
    }

    @Override
    public long index() {
        return blocking.index();
    }

    @Override
    public void prepare(String name, String service, String tagFilter) {
        prepared.define(name, service, tagFilter);
    }

    @Override
    public List<String> executeQuery(String name, String failingNodes) {
        PreparedQueries.Query query = prepared.query(name);
        List<PreparedQueries.Candidate> candidates = new ArrayList<>();
        for (String node : catalog.services(query.service())) {
            boolean healthy = checks.instanceStatus(node, query.service()) != HealthChecks.Status.CRITICAL;
            candidates.add(new PreparedQueries.Candidate(node,
                    nodeTags.getOrDefault(node, List.of()), healthy));
        }
        List<String> failing = failingNodes == null || failingNodes.isBlank()
                ? List.of()
                : List.of(failingNodes.split(","));
        return prepared.execute(name, candidates, node -> !failing.contains(node));
    }

    @Override
    public String session(String node, long ttlTicks) {
        Sessions.Session session = sessions.create(node, ttlTicks, clock);
        return session.id();
    }

    @Override
    public void renew(String sessionId) {
        sessions.renew(sessionId, clock);
    }

    @Override
    public void invalidate(String sessionId) {
        sessions.invalidate(sessionId);
        locks.onSessionInvalid(sessionId);
    }

    @Override
    public long tick() {
        clock++;
        for (String expired : sessions.tick(clock)) {
            locks.onSessionInvalid(expired);
        }
        return clock;
    }

    @Override
    public String lock(String key, String sessionId) {
        return locks.lock(key, sessionId) ? "ACQUIRED" : "QUEUED";
    }

    @Override
    public String semaphore(String key, String sessionId, int limit) {
        return locks.semaphore(key, sessionId, limit) ? "ACQUIRED" : "QUEUED";
    }

    @Override
    public String lockHolder(String key) {
        return locks.lockHolder(key);
    }

    @Override
    public List<String> converge(List<String> memberPairs) {
        return AntiEntropies.converge(catalog, memberPairs);
    }

    @Override
    public List<String> topologyShape() {
        return List.of("service", "name", "traceId", "spanId");
    }
}
