package cn.chyuan.ai.observability.domain.barrierkernel.service;

import java.util.HashSet;
import java.util.Set;

/**
 * 屏障注入（工单 1045 EN1，flink checkpoint barrier 思想）。
 * source 周期注入/barrier 携带 checkpointId/重复 id 拒绝。
 */
public final class Barriers {

    /** 屏障：checkpointId + 注入源 */
    public record Barrier(long checkpointId, String source) {

        public Barrier {
            if (checkpointId <= 0) {
                throw new IllegalArgumentException("checkpointId 须为正: " + checkpointId);
            }
            if (source == null || source.isEmpty()) {
                throw new IllegalArgumentException("source 为空");
            }
        }
    }

    /** 周期注入器：每 intervalTicks 产出一枚 barrier，id 单调递增 */
    public static final class Injector {
        private final long intervalTicks;
        private long nextAt;
        private long lastId;

        public Injector(long intervalTicks, long startAt) {
            if (intervalTicks <= 0) {
                throw new IllegalArgumentException("注入间隔须为正: " + intervalTicks);
            }
            this.intervalTicks = intervalTicks;
            this.nextAt = startAt;
        }

        /** 到点注入，否则返回 null */
        public Barrier maybeInject(long now) {
            if (now < nextAt) {
                return null;
            }
            nextAt += intervalTicks;
            return new Barrier(++lastId, "source-1");
        }

        public long lastId() {
            return lastId;
        }
    }

    /** 接收侧 id 查重：重复 checkpointId 拒绝 */
    public static final class Seen {
        private final Set<Long> seen = new HashSet<>();

        public void accept(long checkpointId) {
            if (!seen.add(checkpointId)) {
                throw new IllegalStateException("重复 checkpointId: " + checkpointId);
            }
        }

        public int size() {
            return seen.size();
        }
    }

    private Barriers() {
    }
}
