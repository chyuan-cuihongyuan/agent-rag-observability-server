package cn.chyuan.ai.observability.domain.mining.service;

import cn.chyuan.ai.observability.domain.evaluate.service.PatrolSummaryProvider;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseAttribution;
import cn.chyuan.ai.observability.domain.mining.service.CaseAttributionService.AttributionStat;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundSummary;
import cn.chyuan.ai.observability.domain.patrol.service.PatrolQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 报告摘要提供者实现（工单 0175 X6）— 由巡检查询与归因统计装配 markdown 摘要行。
 */
@Slf4j
@Service
public class PatrolSummaryProviderImpl implements PatrolSummaryProvider {

    private final PatrolQueryService patrolQueryService;
    private final CaseAttributionService caseAttributionService;

    public PatrolSummaryProviderImpl(PatrolQueryService patrolQueryService,
                                     CaseAttributionService caseAttributionService) {
        this.patrolQueryService = patrolQueryService;
        this.caseAttributionService = caseAttributionService;
    }

    @Override
    public String patrolSummary() {
        PatrolRoundSummary s = patrolQueryService.queryLatestRound();
        if (s == null || s.getRoundId() == null) {
            return null;
        }
        return String.format("最近轮次 %s：共 %d 拨测，成功 %d、失败 %d、超时 %d，平均分 %s（%s）",
                s.getRoundId(), s.getTotal(), s.getSuccess(), s.getFail(), s.getTimeout(),
                s.getAvgScore() == null ? "-" : String.format("%.4f", s.getAvgScore()),
                s.getFinishedAt());
    }

    @Override
    public String attributionSummary() {
        Map<CaseAttribution, AttributionStat> stats =
                caseAttributionService.summarize(caseAttributionService.loadAttributed(null, null, null));
        int total = stats.values().stream().mapToInt(st -> st.count).sum();
        if (total == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder("已标注 ").append(total).append(" 条：");
        stats.forEach((attr, st) -> sb.append(attr.getCode()).append(" ")
                .append(st.count).append("（")
                .append(String.format("%.1f%%", st.ratio * 100)).append("））；"));
        return sb.toString();
    }
}
