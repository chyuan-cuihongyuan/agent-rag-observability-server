package cn.chyuan.ai.observability.domain.loadkernel.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * VU 生命周期（工单 1144 EY2，k6 思想）。
 * 启动爬升至目标 VU/迭代执行回收执/VU 中断 graceful 降容/VU 编号不复用。
 */
public final class VirtualUsers {

    private final Set<Integer> active = new LinkedHashSet<>();
    private final Deque<Integer> retired = new ArrayDeque<>();
    private long idCounter;

    /** 爬升：补齐至目标数，编号只增不复用 */
    public void scaleTo(int target) {
        if (target < 0) {
            throw new IllegalArgumentException("目标 VU 不能为负: " + target);
        }
        while (active.size() < target) {
            active.add((int) ++idCounter);
        }
        while (active.size() > target) {
            Integer oldest = active.iterator().next();
            active.remove(oldest);
            retired.add(oldest);
        }
    }

    /** 中断降容：graceful，编号退场不复用 */
    public void interrupt(int count) {
        scaleTo(Math.max(0, active.size() - count));
    }

    /** 一次迭代：返回执行 VU 编号；无活跃 VU 返回 -1 */
    public int iterate() {
        if (active.isEmpty()) {
            return -1;
        }
        return active.iterator().next();
    }

    public int activeCount() {
        return active.size();
    }

    public int hiredTotal() {
        return (int) idCounter;
    }

    public boolean retiredIdsNeverReused() {
        for (Integer id : retired) {
            if (id <= idCounter && active.contains(id)) {
                return false;
            }
        }
        return true;
    }
}
