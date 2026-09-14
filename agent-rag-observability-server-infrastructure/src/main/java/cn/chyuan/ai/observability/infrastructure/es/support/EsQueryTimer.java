package cn.chyuan.ai.observability.infrastructure.es.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * ES 慢查询计时 — 超阈值 warn 日志（工单 0418/0419，SELFLOOP3 loop-310）
 * <p>
 * 显式函数式包装（ElasticsearchClient 不可装饰）：supplier 允许抛 IOException，
 * finally 计时保证异常路径也有观测，异常原样上抛不吞。
 */
@Slf4j
@Component
public class EsQueryTimer {

    private final long thresholdMs;

    public EsQueryTimer(
            @Value("${observability.es.slow-query-threshold-ms:1000}") long thresholdMs) {
        this.thresholdMs = Math.max(1, thresholdMs);
    }

    @FunctionalInterface
    public interface EsQuery<T> {
        T run() throws IOException;
    }

    /** 包装一次 ES 查询 — 慢查询输出具名 warn 日志，返回值/异常原样透传 */
    public <T> T timed(String label, EsQuery<T> query) throws IOException {
        long start = System.nanoTime();
        try {
            return query.run();
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000;
            if (isSlow(costMs)) {
                log.warn("ES 慢查询: {} 耗时 {}ms（阈值 {}ms）", label, costMs, thresholdMs);
            }
        }
    }

    /** 阈值判定 — 测试可见边界语义 */
    boolean isSlow(long costMs) {
        return costMs >= thresholdMs;
    }
}
