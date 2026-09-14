package cn.chyuan.ai.observability.domain.evaluate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * 评测并发闸门 — 限制同时执行的评测任务数，饱和即拒（bulkhead 隔离思想，工单 0404/0405）
 * <p>
 * 评测任务含长时间 LLM judge 调用，与 trace 写入共享 observeExecutor 线程池；
 * 闸门防止评测洪峰占满线程池挤掉写入吞吐。
 */
@Slf4j
@Component
public class EvalConcurrencyGuard {

    private final Semaphore permits;

    public EvalConcurrencyGuard(
            @Value("${observability.eval.max-concurrent-tasks:2}") int maxConcurrentTasks) {
        this.permits = new Semaphore(Math.max(1, maxConcurrentTasks));
    }

    /** 尝试获取执行许可 — 非阻塞，饱和返回 false */
    public boolean tryAcquire() {
        return permits.tryAcquire();
    }

    /** 归还许可 — 必须与 tryAcquire 成功路径配对（finally 释放） */
    public void release() {
        permits.release();
    }

    /** 当前可用许可数（观测/测试用） */
    public int availablePermits() {
        return permits.availablePermits();
    }
}
