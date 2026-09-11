package cn.chyuan.ai.observability.trigger.scheduler;

import cn.chyuan.ai.observability.domain.insight.service.DriftDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 漂移检测调度器（工单 0154 U8）— 默认关闭（drift.enabled=false）；
 * 按 cron（默认每周一 05:00）执行本周 vs 上周窗对比；防重入；轮次级异常兜底。
 */
@Slf4j

@Component
@ConditionalOnProperty(name = "drift.enabled", havingValue = "true")
public class DriftScheduler {

    @Resource
    private cn.chyuan.ai.observability.domain.scheduler.SchedulerRunRegistry schedulerRegistry;

    private final DriftDetectionService driftDetectionService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public DriftScheduler(
            DriftDetectionService driftDetectionService) {
        this.driftDetectionService = driftDetectionService;
    }

    @Scheduled(cron = "${drift.cron:0 0 5 * * MON}")
    public void detect() {
        if (!running.compareAndSet(false, true)) {
            log.warn("上一轮漂移检测尚未结束，跳过本轮调度");
            return;
        }
        try {
            driftDetectionService.runOnce();
        } catch (Exception e) {
            log.error("漂移检测轮次执行异常", e);
        } finally {
            schedulerRegistry.report("drift", true, null);
            running.set(false);
        }
    }
}
