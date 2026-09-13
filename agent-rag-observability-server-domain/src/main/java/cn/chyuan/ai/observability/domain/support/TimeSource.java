package cn.chyuan.ai.observability.domain.support;

/**
 * 单调时间源（SELFLOOP2 loop-238）：耗时测量用 nanoTime，免受 NTP 墙钟跳变影响
 * （墙钟可能回拨导致耗时为负、前跳导致虚高）。纯静态，domain 无 Spring 依赖。
 */
public final class TimeSource {

    private TimeSource() {
    }

    public static Started started() {
        return new Started(System.nanoTime());
    }

    public static final class Started {
        private final long startNanos;

        private Started(long startNanos) {
            this.startNanos = startNanos;
        }

        /** 距 started() 的毫秒数（单调，恒 ≥ 0） */
        public long elapsedMillis() {
            return (System.nanoTime() - startNanos) / 1_000_000;
        }
    }
}
