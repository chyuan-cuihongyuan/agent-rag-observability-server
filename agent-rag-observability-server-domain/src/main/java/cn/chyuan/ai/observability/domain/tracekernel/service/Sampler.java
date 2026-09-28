package cn.chyuan.ai.observability.domain.tracekernel.service;

/**
 * 采样器（工单 0972 EE5，jaeger 思想）。
 * 概率采样确定性决策/限速令牌桶/未采样只记骨架。
 */
public final class Sampler {

    /** 令牌桶限速：容量固定、每步补给 */
    public static final class RateLimiter {
        private final double capacity;
        private final double refillPerTick;
        private double tokens;

        public RateLimiter(double capacity, double refillPerTick) {
            if (capacity <= 0 || refillPerTick < 0) {
                throw new IllegalArgumentException("限速参数非法: " + capacity + "/" + refillPerTick);
            }
            this.capacity = capacity;
            this.refillPerTick = refillPerTick;
            this.tokens = capacity;
        }

        /** 取一个令牌：空桶拒绝 */
        public boolean tryAcquire() {
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }

        /** 步进补给（封顶容量） */
        public void tick() {
            tokens = Math.min(capacity, tokens + refillPerTick);
        }

        public double tokens() {
            return tokens;
        }
    }

    private final double probability;
    private final RateLimiter limiter;

    public Sampler(double probability, RateLimiter limiter) {
        if (probability < 0 || probability > 1) {
            throw new IllegalArgumentException("概率非法: " + probability);
        }
        this.probability = probability;
        this.limiter = limiter;
    }

    /** 采样决策：概率（对 traceId 确定性散列）+ 限速令牌桶双重把关 */
    public boolean sample(String traceId) {
        double uniform = (traceId.hashCode() & 0x7fffffff) / 2147483648.0;
        if (uniform >= probability) {
            return false;
        }
        return limiter == null || limiter.tryAcquire();
    }

    /** 决策读数：true 全量、false 仅骨架（未采样只记骨架由调用方落存储） */
    public String decision(String traceId) {
        return sample(traceId) ? "full" : "skeleton";
    }
}
