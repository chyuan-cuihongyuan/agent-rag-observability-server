package cn.chyuan.ai.observability.domain.fiberkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * lane 优先级（工单 0789 CO5，react Fiber 思想）。
 * lane 数值越小优先级越高/位图合并与最高位提取/批量任务稳定调度（同 lane 保插入序）。
 */
public final class Lanes {

    /** 调度任务：id 与 lane */
    public record Task(String id, int lane) {
    }

    /** 稳定调度：按 lane 升序，同 lane 保持插入序 */
    public List<Task> schedule(List<Task> tasks) {
        List<Task> out = new ArrayList<>(tasks);
        out.sort((a, b) -> Integer.compare(a.lane(), b.lane()));
        return out;
    }

    /** 位图合并 */
    public int merge(int... lanes) {
        int mask = 0;
        for (int lane : lanes) {
            if (lane < 0 || lane > 31) {
                throw new IllegalArgumentException("lane 越界: " + lane);
            }
            mask |= 1 << lane;
        }
        return mask;
    }

    /** 最高优先级 = 位图中最低 lane 位；空位图抛出 */
    public int highest(int bitmask) {
        if (bitmask == 0) {
            throw new IllegalArgumentException("空 lane 位图");
        }
        return Integer.numberOfTrailingZeros(bitmask);
    }

    public boolean contains(int bitmask, int lane) {
        return (bitmask & (1 << lane)) != 0;
    }
}
