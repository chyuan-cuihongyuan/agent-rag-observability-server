package cn.chyuan.ai.observability.trigger.scheduler;

import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseStatus;
import cn.chyuan.ai.observability.domain.mining.service.CaseCandidateQueryService;
import cn.chyuan.ai.observability.domain.mining.service.CaseMiningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Case 自动挖掘调度器（工单 0138 S2）— 默认关闭（mining.auto-enabled=false），
 * 开启后按 cron 周期采集三来源候选并自动回填错题集（在线→离线 Loop 全自动形态）。
 * <p>
 * 配置面：
 * <ul>
 *   <li>mining.auto-enabled — 默认 false</li>
 *   <li>mining.auto-cron — 默认每 15 分钟</li>
 *   <li>mining.low-score-threshold — 低分阈值，默认 0.6</li>
 *   <li>mining.scan-limit — 单来源单轮扫描上限，默认 50（钳制 ≤200）</li>
 *   <li>mining.auto-dataset — 自动回填目标错题集名称，默认「错题本」</li>
 * </ul>
 */
@Slf4j

@Component
@ConditionalOnProperty(name = "mining.auto-enabled", havingValue = "true")
public class CaseMiningScheduler {

    @Resource
    private cn.chyuan.ai.observability.domain.scheduler.SchedulerRunRegistry schedulerRegistry;

    private final CaseMiningService caseMiningService;
    private final CaseCandidateQueryService candidateQueryService;

    @Value("${mining.low-score-threshold:0.6}")
    private double lowScoreThreshold;

    @Value("${mining.scan-limit:50}")
    private int scanLimit;

    @Value("${mining.auto-dataset:}")
    private String autoDataset;

    /** 防重入：上一轮未结束时跳过本轮调度 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CaseMiningScheduler(
            CaseMiningService caseMiningService,
                               CaseCandidateQueryService candidateQueryService) {
        this.caseMiningService = caseMiningService;
        this.candidateQueryService = candidateQueryService;
    }

    @Scheduled(cron = "${mining.auto-cron:0 */15 * * * *}")
    public void mine() {
        if (!running.compareAndSet(false, true)) {
            log.warn("上一轮 Case 挖掘尚未结束，跳过本轮调度");
            return;
        }
        try {
            CaseMiningService.CollectOutcome outcome = caseMiningService.collect(
                    CaseMiningService.MiningConfig.of(lowScoreThreshold, scanLimit));
            if (outcome.total() > 0) {
                // 自动回填处置全部 PENDING（新采集候选与历史遗留一并回填；promote 内部幂等）
                List<Long> pendingIds = candidateQueryService.queryList(null, CaseStatus.PENDING, 1, 100)
                        .stream().map(c -> c.getId()).toList();
                if (!pendingIds.isEmpty()) {
                    CaseMiningService.PromoteOutcome promoted =
                            caseMiningService.promote(pendingIds, datasetName());
                    log.info("Case 自动回填完成: promoted={}, datasetId={}",
                            promoted.promoted(), promoted.datasetId());
                }
            }
        } catch (Exception e) {
            log.error("Case 自动挖掘轮次执行异常", e);
        } finally {
            schedulerRegistry.report("mining", true, null);
            running.set(false);
        }
    }

    private String datasetName() {
        return autoDataset == null || autoDataset.isBlank() ? CaseMiningService.DEFAULT_WRONG_DATASET_NAME : autoDataset;
    }
}
