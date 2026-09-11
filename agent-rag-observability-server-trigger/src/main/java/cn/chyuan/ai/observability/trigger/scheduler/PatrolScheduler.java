package cn.chyuan.ai.observability.trigger.scheduler;

import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundConfig;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundSummary;
import cn.chyuan.ai.observability.domain.patrol.service.PatrolProbeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 巡检定时调度器（工单 0137 S1）— 默认关闭（patrol.enabled=false），
 * 显式开启后按 cron 周期拨测；防重入锁保证上一轮未完成时不叠加下一轮。
 * <p>
 * 配置面（全部有默认值）：
 * <ul>
 *   <li>patrol.enabled — 默认 false</li>
 *   <li>patrol.cron — 默认每 30 分钟</li>
 *   <li>patrol.timeout-ms — 单次拨测超时预算，默认 30000</li>
 *   <li>patrol.agent-id — 目标智能体（空则用在线回放 provider 默认值）</li>
 *   <li>patrol.queries — 固定查询集（逗号分隔；优先于 datasetId）</li>
 *   <li>patrol.dataset-id — golden 池数据集 ID（种子回退来源）</li>
 * </ul>
 */
@Slf4j

@Component
@EnableScheduling
@ConditionalOnProperty(name = "patrol.enabled", havingValue = "true")
public class PatrolScheduler {

    @Resource
    private cn.chyuan.ai.observability.domain.scheduler.SchedulerRunRegistry schedulerRegistry;

    private final PatrolProbeService patrolProbeService;

    @Value("${patrol.timeout-ms:30000}")
    private long timeoutMs;

    @Value("${patrol.agent-id:}")
    private String agentId;

    @Value("${patrol.dataset-id:}")
    private String datasetId;

    /** 固定查询集（逗号分隔）；@Value 到 List 不便承载空默认，这里收字符串再切分 */
    @Value("${patrol.queries:}")
    private String queriesCsv;

    /** 防重入：上一轮未结束时跳过本轮调度 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PatrolScheduler(
            PatrolProbeService patrolProbeService) {
        this.patrolProbeService = patrolProbeService;
    }

    @Scheduled(cron = "${patrol.cron:0 */30 * * * *}")
    public void patrol() {
        if (!running.compareAndSet(false, true)) {
            log.warn("上一轮巡检尚未结束，跳过本轮调度");
            return;
        }
        try {
            patrolProbeService.runRound(buildConfig());
        } catch (Exception e) {
            // 轮次级兜底：调度异常不向上抛（避免影响调度器线程健康）
            log.error("巡检轮次执行异常", e);
        } finally {
            schedulerRegistry.report("patrol", true, null);
            running.set(false);
        }
    }

    /** 装配轮次配置（供调度与手动触发共用） */
    public PatrolRoundConfig buildConfig() {
        List<String> queries = queriesCsv == null || queriesCsv.isBlank()
                ? List.of()
                : Arrays.stream(queriesCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        return PatrolRoundConfig.builder()
                .timeoutMs(timeoutMs)
                .agentId(blankToNull(agentId))
                .queries(queries)
                .datasetId(blankToNull(datasetId))
                .build();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
