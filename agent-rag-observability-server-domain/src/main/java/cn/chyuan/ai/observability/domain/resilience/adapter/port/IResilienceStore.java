package cn.chyuan.ai.observability.domain.resilience.adapter.port;

import cn.chyuan.ai.observability.domain.resilience.service.BackfillJob;
import cn.chyuan.ai.observability.domain.resilience.service.LagSnapshot;
import cn.chyuan.ai.observability.domain.resilience.service.QualityRunner;
import cn.chyuan.ai.observability.domain.resilience.service.QualityRule;
import cn.chyuan.ai.observability.domain.resilience.service.SlaMiss;

import java.util.List;

/**
 * 调度韧性聚合存储端口（工单 0220-0223 AD 簇；infrastructure 落双方言表 / 内存实现）—
 * lag_snapshot（22）/ quality_rule（23）/ quality_result（24）/ backfill_job（25）/ sla_miss（26）。
 *
 * @author chyuan
 */
public interface IResilienceStore {

    // ── lag 快照（AD1） ──
    void saveLag(LagSnapshot snapshot);

    List<LagSnapshot> recentLags(int limit);

    // ── 质量规则（AD2） ──
    void upsertRule(QualityRule rule);

    void deleteRule(String ruleName);

    QualityRule findRule(String ruleName);

    List<QualityRule> listRules();

    // ── 质量校验结果（AD2） ──
    void saveResult(QualityRunner.QualityResult result);

    List<QualityRunner.QualityResult> recentResults(int limit);

    // ── 回填任务（AD3） ──
    void upsertBackfill(BackfillJob job);

    BackfillJob findBackfill(String id);

    // ── SLA 错过（AD4） ──
    void saveSlaMiss(SlaMiss miss);

    List<SlaMiss> recentSlaMisses(int limit);
}
