package cn.chyuan.ai.observability.domain.fiberkernel.service;

/**
 * 双缓冲（工单 0790 CO6，react Fiber 思想）。
 * current 与 workInProgress 交替/commit 后互换/未完成渲染丢弃回滚。
 */
public final class DoubleBuffer {

    private Fiber current;
    private Fiber workInProgress;

    /** 开始一次渲染：登记 WIP */
    public void begin(Fiber wip) {
        this.workInProgress = wip;
    }

    /** 提交：WIP 转正（alternate 对位由协调器逐节点建立） */
    public Fiber commit() {
        if (workInProgress == null) {
            throw new IllegalStateException("无进行中渲染");
        }
        Fiber committed = workInProgress;
        current = committed;
        workInProgress = null;
        return committed;
    }

    /** 丢弃未完成渲染（回滚） */
    public void discard() {
        workInProgress = null;
    }

    public Fiber current() {
        return current;
    }

    public Fiber workInProgress() {
        return workInProgress;
    }
}
