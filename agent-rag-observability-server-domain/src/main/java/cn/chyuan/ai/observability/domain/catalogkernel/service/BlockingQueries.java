package cn.chyuan.ai.observability.domain.catalogkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 阻塞查询（工单 1092 ES3，consul 思想）。
 * index 单调递增/变更唤醒全部等待者一批/无变更阻塞让出（poll 返回 null）/
 * 唤醒快照取变更后目录（快照一致性）。
 */
public final class BlockingQueries {

    /** 等待者 */
    public static final class Waiter {
        private final String id;
        private final long sinceIndex;
        private String snapshot;

        Waiter(String id, long sinceIndex) {
            this.id = id;
            this.sinceIndex = sinceIndex;
        }

        public String id() {
            return id;
        }

        public long sinceIndex() {
            return sinceIndex;
        }
    }

    private long index = 1;
    private final Map<String, Waiter> waiters = new LinkedHashMap<>();
    private final Map<String, String> delivered = new LinkedHashMap<>();
    private final Supplier<List<String>> snapshot;

    public BlockingQueries(Supplier<List<String>> snapshot) {
        this.snapshot = snapshot;
    }

    /** 轮询：index 已推进立即返回快照；无变更登记等待者并返回 null（阻塞让出） */
    public String poll(String waiterId, long sinceIndex) {
        if (waiterId == null || waiterId.isBlank()) {
            throw new IllegalArgumentException("等待者不能为空");
        }
        if (sinceIndex < 0) {
            throw new IllegalArgumentException("index 不可为负: " + sinceIndex);
        }
        if (index > sinceIndex) {
            return String.join(",", snapshot.get());
        }
        waiters.put(waiterId, new Waiter(waiterId, sinceIndex));
        return null;
    }

    /** 变更：index 推进并唤醒全部等待者一批（快照投递），返回被唤醒 id 列表 */
    public List<String> change() {
        index++;
        if (waiters.isEmpty()) {
            return List.of();
        }
        String snapshotNow = String.join(",", snapshot.get());
        List<String> woken = new ArrayList<>();
        for (Waiter waiter : waiters.values()) {
            delivered.put(waiter.id(), snapshotNow);
            woken.add(waiter.id());
        }
        waiters.clear();
        return woken;
    }

    /** 等待者取唤醒结果（一次性取，取后清除）：未唤醒返回 null */
    public String take(String waiterId) {
        return delivered.remove(waiterId);
    }

    public long index() {
        return index;
    }

    public int waiting() {
        return waiters.size();
    }
}
