package cn.chyuan.ai.observability.domain.resilience.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 回填任务分片规划纯函数（工单 0222 AD3，借鉴 Airflow backfill）—
 * 把 [rangeStart, rangeEnd]（闭区间，epoch 毫秒或任意长整型键空间）均分为
 * shardCount 个连续分片（末片吸收余数）。
 *
 * @author chyuan
 */
public final class BackfillPlanner {

    /** 分片（闭区间 [start, end]） */
    public record Shard(int index, long start, long end) {
    }

    private BackfillPlanner() {
    }

    /** 均分：range 非法（start>end）或 shardCount<1 抛 IllegalArgumentException */
    public static List<Shard> plan(long rangeStart, long rangeEnd, int shardCount) {
        if (rangeStart > rangeEnd) {
            throw new IllegalArgumentException("回填范围非法: " + rangeStart + " > " + rangeEnd);
        }
        if (shardCount < 1) {
            throw new IllegalArgumentException("分片数至少为 1");
        }
        long total = rangeEnd - rangeStart + 1;
        int effective = (int) Math.min(shardCount, total);
        long base = total / effective;
        long remainder = total % effective;
        List<Shard> shards = new ArrayList<>(effective);
        long cursor = rangeStart;
        for (int i = 0; i < effective; i++) {
            long size = base + (i < remainder ? 1 : 0);
            shards.add(new Shard(i, cursor, cursor + size - 1));
            cursor += size;
        }
        return shards;
    }

    /** 未完成分片（已完成集之外的按序返回） */
    public static List<Shard> pending(List<Shard> all, TreeSet<Integer> completedIndexes) {
        List<Shard> out = new ArrayList<>();
        for (Shard shard : all) {
            if (!completedIndexes.contains(shard.index())) {
                out.add(shard);
            }
        }
        return out;
    }
}
