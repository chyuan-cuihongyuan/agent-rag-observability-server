package cn.chyuan.ai.observability.infrastructure.config;

import cn.chyuan.ai.observability.infrastructure.metrics.ObserveMetrics;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncExecutorConfig {

    @Value("${observability.executor.core-pool-size:4}")
    private int corePoolSize;

    @Value("${observability.executor.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${observability.executor.queue-capacity:2000}")
    private int queueCapacity;

    @Resource
    private ObserveMetrics observeMetrics;

    @Bean("observeExecutor")
    public Executor observeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("observe-");
        // 队列满时记录指标和日志，而非静默丢弃
        executor.setRejectedExecutionHandler((r, ex) -> {
            observeMetrics.recordWriteFailure("executor_rejected");
            log.warn("observeExecutor task rejected, activeCount={}, queueSize={}",
                    ex.getActiveCount(), ex.getQueue().size());
        });
        executor.initialize();
        return executor;
    }
}
