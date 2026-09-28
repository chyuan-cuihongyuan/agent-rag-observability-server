package cn.chyuan.ai.observability.domain.pipekernel.service;

import java.util.ArrayDeque;

/**
 * 有界缓冲（工单 0980 EF5，vector 思想）。
 * 有界缓冲/满时 block 拒绝/容量可配。
 */
public final class Buffer {

    private final ArrayDeque<Object> queue = new ArrayDeque<>();
    private final int capacity;

    public Buffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("容量非法: " + capacity);
        }
        this.capacity = capacity;
    }

    /** 入队：满时拒绝（同步管线的 block 语义） */
    public void push(Object item) {
        if (queue.size() >= capacity) {
            throw new IllegalStateException("缓冲已满拒绝: " + capacity);
        }
        queue.addLast(item);
    }

    /** 出队：空返回 null */
    public Object poll() {
        return queue.pollFirst();
    }

    public int size() {
        return queue.size();
    }

    public int capacity() {
        return capacity;
    }
}
