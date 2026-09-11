package cn.chyuan.ai.observability.trigger.scheduler;

import cn.chyuan.ai.observability.domain.insight.service.RetentionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志保留清理调度器（工单 0152 U6）— 默认关闭（retention.enabled=false）；
 * 按 cron 清理超过 retention.days 的日志；防重入；轮次级异常兜底。
 * 指标：retention_deleted_total（按表打点，通过日志与返回值暴露）。
 */
@Slf4j

@Component
@ConditionalOnProperty(name = "retention.enabled", havingValue = "true")
public class RetentionScheduler {

    @Resource
    private cn.chyuan.ai.observability.domain.scheduler.SchedulerRunRegistry schedulerRegistry;

    private final RetentionService retentionService;

    @Value("${retention.days:90}")
    private int retentionDays;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public RetentionScheduler(
            RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${retention.cron:0 0 4 * * *}")
    public void purge() {
        if (!running.compareAndSet(false, true)) {
            log.warn("上一轮保留清理尚未结束，跳过本轮调度");
            return;
        }
        try {
            Map<String, Long> deleted = retentionService.purge(LocalDateTime.now().minusDays(retentionDays));
            long total = deleted.values().stream().mapToLong(Long::longValue).sum();
            log.info("retention_deleted_total={}", total);
        } catch (Exception e) {
            log.error("保留清理轮次执行异常", e);
        } finally {
            schedulerRegistry.report("retention", true, null);
            running.set(false);
        }
    }
}
