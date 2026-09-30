package cn.chyuan.ai.observability.domain.catalogkernel.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 锁与信号量（工单 1095 ES6，consul 思想）。
 * 锁单持有者互斥（他人请求排队等待）/持有者会话失效自动释放/
 * 信号量 N 槽并发上限/等待者依序接管（FIFO）。
 */
public final class Locks {

    private static final class LockEntry {
        String holder;
        final Deque<String> waiters = new ArrayDeque<>();
    }

    private static final class SemaphoreEntry {
        final int limit;
        final Set<String> holders = new LinkedHashSet<>();
        final Deque<String> waiters = new ArrayDeque<>();

        SemaphoreEntry(int limit) {
            this.limit = limit;
        }
    }

    private final Map<String, LockEntry> locks = new LinkedHashMap<>();
    private final Map<String, SemaphoreEntry> semaphores = new LinkedHashMap<>();

    /** 抢锁：空闲或自持获得 true；他人持有时入等待队列返回 false */
    public boolean lock(String key, String sessionId) {
        requireKey(key);
        requireSession(sessionId);
        LockEntry entry = locks.computeIfAbsent(key, ignored -> new LockEntry());
        if (entry.holder == null) {
            entry.holder = sessionId;
            return true;
        }
        if (entry.holder.equals(sessionId)) {
            return true;
        }
        if (!entry.waiters.contains(sessionId)) {
            entry.waiters.addLast(sessionId);
        }
        return false;
    }

    /** 释放锁：持有者释放后等待者队首接管；非持有者从等待队列移除 */
    public String releaseLock(String key, String sessionId) {
        LockEntry entry = locks.get(key);
        if (entry == null || entry.holder == null) {
            throw new IllegalArgumentException("未持有锁拒绝释放: " + key);
        }
        if (entry.holder.equals(sessionId)) {
            entry.holder = entry.waiters.pollFirst();
            return entry.holder;
        }
        entry.waiters.remove(sessionId);
        return entry.holder;
    }

    /** 会话失效联动：释放其全部锁与信号量槽，返回接管的 (key:session) 列表 */
    public List<String> onSessionInvalid(String sessionId) {
        List<String> promoted = new ArrayList<>();
        for (Map.Entry<String, LockEntry> lock : locks.entrySet()) {
            LockEntry entry = lock.getValue();
            boolean held = sessionId.equals(entry.holder);
            boolean waiting = entry.waiters.remove(sessionId);
            if (held) {
                entry.holder = entry.waiters.pollFirst();
                if (entry.holder != null) {
                    promoted.add("lock:" + lock.getKey() + ":" + entry.holder);
                }
            } else if (waiting && entry.holder == null) {
                entry.holder = entry.waiters.pollFirst();
            }
        }
        for (Map.Entry<String, SemaphoreEntry> semaphore : semaphores.entrySet()) {
            SemaphoreEntry entry = semaphore.getValue();
            if (entry.holders.remove(sessionId)) {
                String next = entry.waiters.pollFirst();
                if (next != null) {
                    entry.holders.add(next);
                    promoted.add("sem:" + semaphore.getKey() + ":" + next);
                }
            } else {
                entry.waiters.remove(sessionId);
            }
        }
        return promoted;
    }

    /** 信号量：未满获得 true 并占槽；满时入等待队列 false */
    public boolean semaphore(String key, String sessionId, int limit) {
        requireKey(key);
        requireSession(sessionId);
        if (limit <= 0) {
            throw new IllegalArgumentException("信号量上限必须为正: " + limit);
        }
        SemaphoreEntry entry = semaphores.computeIfAbsent(key, ignored -> new SemaphoreEntry(limit));
        if (entry.holders.contains(sessionId)) {
            return true;
        }
        if (entry.holders.size() < entry.limit) {
            entry.holders.add(sessionId);
            return true;
        }
        if (!entry.waiters.contains(sessionId)) {
            entry.waiters.addLast(sessionId);
        }
        return false;
    }

    /** 释放信号量槽：等待者队首接管，返回接管者或 null */
    public String releaseSemaphore(String key, String sessionId) {
        SemaphoreEntry entry = semaphores.get(key);
        if (entry == null || !entry.holders.remove(sessionId)) {
            throw new IllegalArgumentException("未占槽拒绝释放: " + key);
        }
        String next = entry.waiters.pollFirst();
        if (next != null) {
            entry.holders.add(next);
        }
        return next;
    }

    public String lockHolder(String key) {
        LockEntry entry = locks.get(key);
        return entry == null ? null : entry.holder;
    }

    public Set<String> semaphoreHolders(String key) {
        SemaphoreEntry entry = semaphores.get(key);
        return entry == null ? Set.of() : Set.copyOf(entry.holders);
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("键不能为空");
        }
    }

    private void requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("会话不能为空");
        }
    }
}
