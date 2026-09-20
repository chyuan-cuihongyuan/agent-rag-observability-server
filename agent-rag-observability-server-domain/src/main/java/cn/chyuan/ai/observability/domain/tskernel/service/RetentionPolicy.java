package cn.chyuan.ai.observability.domain.tskernel.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 保留策略（工单 0484 BF5，influxdb retention policy 思想）。
 * 保留时长配置/过期块淘汰（时钟端口注入，块最大时间戳 < now−retention 判过期）/
 * 淘汰后索引与块一致性清理（无块引用的序列从索引移除）。
 */
public class RetentionPolicy {

    /** 时钟端口 */
    @FunctionalInterface
    public interface ClockPort {
        long nowMillis();
    }

    /** 淘汰结果：淘汰块数 + 清理的序列 id */
    public record EvictionResult(int evictedBlocks, Set<Long> clearedSeries) {
    }

    private final long retentionMillis;
    private final ClockPort clock;
    /** seriesId → 块列表 */
    private final Map<Long, List<TimeBlocker.Block>> blocksBySeries = new HashMap<>();

    public RetentionPolicy(long retentionMillis, ClockPort clock) {
        if (retentionMillis <= 0) {
            throw new IllegalArgumentException("保留时长须 > 0: " + retentionMillis);
        }
        this.retentionMillis = retentionMillis;
        this.clock = clock;
    }

    /** 登记序列块（块须属于该序列） */
    public synchronized void attach(long seriesId, List<TimeBlocker.Block> blocks) {
        blocksBySeries.computeIfAbsent(seriesId, k -> new ArrayList<>()).addAll(blocks);
    }

    /** 过期淘汰：块按 maxTimestamp 判过期；序列块清空后从索引移除（一致性清理） */
    public synchronized EvictionResult evictExpired() {
        long cutoff = clock.nowMillis() - retentionMillis;
        int evicted = 0;
        for (Map.Entry<Long, List<TimeBlocker.Block>> entry : blocksBySeries.entrySet()) {
            evicted += entry.getValue().size();
            entry.getValue().removeIf(block -> block.maxTimestamp() < cutoff);
            evicted -= entry.getValue().size();
        }
        Set<Long> cleared = new LinkedHashSet<>();
        blocksBySeries.entrySet().removeIf(entry -> {
            if (entry.getValue().isEmpty()) {
                cleared.add(entry.getKey());
                return true;
            }
            return false;
        });
        return new EvictionResult(evicted, Set.copyOf(cleared));
    }

    public synchronized int blockCount() {
        return blocksBySeries.values().stream().mapToInt(List::size).sum();
    }

    public synchronized int seriesCount() {
        return blocksBySeries.size();
    }

    public long retentionMillis() {
        return retentionMillis;
    }
}
