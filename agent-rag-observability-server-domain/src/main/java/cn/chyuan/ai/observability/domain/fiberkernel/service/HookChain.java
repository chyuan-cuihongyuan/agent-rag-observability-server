package cn.chyuan.ai.observability.domain.fiberkernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Hook 状态链（工单 0791 CO7，react Fiber 思想）。
 * useState 状态按 hook 索引跨渲染对齐/update 队列入队（值与函数式）与按序消费/hook 数量变化拒绝。
 */
public final class HookChain {

    private final List<Object> states = new ArrayList<>();
    private final List<List<Object>> queues = new ArrayList<>();

    /** 渲染开始：清空本渲染已消费标记 */
    public void beginRender() {
        // 状态与队列跨渲染保留；对齐由 useState 按索引完成
    }

    /** useState：首渲染建立初值；后续渲染按序消费更新队列后返回 */
    public Object useState(int index, Object initial) {
        if (index < 0) {
            throw new IllegalArgumentException("hook 索引为负");
        }
        if (index >= states.size()) {
            if (index != states.size()) {
                throw new IllegalArgumentException("hook 索引跳位: " + index);
            }
            states.add(initial);
            queues.add(new ArrayList<>());
            return states.get(index);
        }
        Object current = states.get(index);
        for (Object update : queues.get(index)) {
            current = apply(update, current);
        }
        queues.get(index).clear();
        states.set(index, current);
        return current;
    }

    /** setState：值更新入队 */
    public void set(int index, Object value) {
        ensure(index);
        queues.get(index).add(value);
    }

    /** setState：函数式更新入队 */
    public void setFn(int index, UnaryOperator<Object> fn) {
        ensure(index);
        queues.get(index).add(fn);
    }

    /** eager 快路径：队列为空的值更新直接落值 */
    public void setEager(int index, Object value) {
        ensure(index);
        if (queues.get(index).isEmpty()) {
            states.set(index, value);
        } else {
            queues.get(index).add(value);
        }
    }

    private Object apply(Object update, Object current) {
        return update instanceof UnaryOperator<?> fn ? ((UnaryOperator<Object>) fn).apply(current) : update;
    }

    private void ensure(int index) {
        if (index < 0 || index >= states.size()) {
            throw new IllegalArgumentException("未挂载 hook: " + index);
        }
    }

    /** 渲染结束：本渲染使用的 hook 数须与已挂载一致 */
    public void endRender(int usedCount) {
        if (usedCount != states.size()) {
            throw new IllegalStateException("hook 数量变化: 已挂 " + states.size() + " 本次 " + usedCount);
        }
    }

    public int hookCount() {
        return states.size();
    }

    public Object stateAt(int index) {
        return states.get(index);
    }
}
