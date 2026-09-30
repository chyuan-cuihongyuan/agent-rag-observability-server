package cn.chyuan.ai.observability.domain.catalogkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * session 生命周期（工单 1094 ES5，consul 思想）。
 * 创建发 ID/TTL 续期延期/到期失效（失效释放全部持有物）/未失效续期拒绝语义与重复失效拒绝。
 */
public final class Sessions {

    /** 会话 */
    public static final class Session {
        private final String id;
        private final String node;
        private final long ttlTicks;
        private long expiryTick;
        private boolean valid = true;
        private final List<Runnable> holds = new ArrayList<>();

        Session(String id, String node, long ttlTicks, long nowTick) {
            this.id = id;
            this.node = node;
            this.ttlTicks = ttlTicks;
            this.expiryTick = nowTick + ttlTicks;
        }

        public String id() {
            return id;
        }

        public String node() {
            return node;
        }

        public boolean valid() {
            return valid;
        }

        public long expiryTick() {
            return expiryTick;
        }
    }

    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private long seq;

    /** 创建：TTL 必须为正 */
    public Session create(String node, long ttlTicks, long nowTick) {
        if (node == null || node.isBlank()) {
            throw new IllegalArgumentException("节点不能为空");
        }
        if (ttlTicks <= 0) {
            throw new IllegalArgumentException("TTL 必须为正: " + ttlTicks);
        }
        Session session = new Session("sess-" + (++seq), node, ttlTicks, nowTick);
        sessions.put(session.id(), session);
        return session;
    }

    /** 续期：到期时刻 = now + TTL；失效会话拒绝 */
    public void renew(String sessionId, long nowTick) {
        Session session = require(sessionId);
        session.expiryTick = nowTick + session.ttlTicks;
    }

    /** 登记持有物：失效会话拒绝 */
    public void hold(String sessionId, Runnable release) {
        Session session = require(sessionId);
        if (!session.valid) {
            throw new IllegalStateException("失效会话拒绝登记持有物: " + sessionId);
        }
        session.holds.add(release);
    }

    /** 步进：到期会话失效并释放全部持有物，返回失效 id */
    public List<String> tick(long nowTick) {
        List<String> expired = new ArrayList<>();
        for (Session session : sessions.values()) {
            if (session.valid && nowTick > session.expiryTick) {
                expired.add(session.id());
            }
        }
        for (String id : expired) {
            invalidate(id);
        }
        return expired;
    }

    /** 主动失效：释放持有物；重复失效拒绝 */
    public void invalidate(String sessionId) {
        Session session = require(sessionId);
        if (!session.valid) {
            throw new IllegalStateException("重复失效拒绝: " + sessionId);
        }
        session.valid = false;
        for (Runnable release : session.holds) {
            release.run();
        }
        session.holds.clear();
    }

    public boolean isValid(String sessionId) {
        Session session = sessions.get(sessionId);
        return session != null && session.valid;
    }

    private Session require(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("未知会话拒绝: " + sessionId);
        }
        return session;
    }
}
