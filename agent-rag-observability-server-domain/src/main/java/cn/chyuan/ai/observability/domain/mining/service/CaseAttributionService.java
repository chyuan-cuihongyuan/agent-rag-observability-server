package cn.chyuan.ai.observability.domain.mining.service;

import cn.chyuan.ai.observability.domain.mining.adapter.repository.ICaseCandidateRepository;
import cn.chyuan.ai.observability.domain.mining.model.entity.CaseCandidateEntity;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseAttribution;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Case 归因标注服务（工单 0139 S3）— 标注工作流（枚举校验 + by/at 留痕）
 * 与归因分布统计（纯函数与端点解耦，时间窗过滤口径：attribution_at）。
 */
@Slf4j
@Service
public class CaseAttributionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ICaseCandidateRepository caseCandidateRepository;

    public CaseAttributionService(ICaseCandidateRepository caseCandidateRepository) {
        this.caseCandidateRepository = caseCandidateRepository;
    }

    /**
     * 标注归因：非法枚举拒绝（IllegalArgumentException），操作留痕 by/at 服务端生成。
     *
     * @return 更新行数（0 = 候选不存在）
     */
    public int label(long id, String attributionCode, String note, String by) {
        CaseAttribution attribution = CaseAttribution.fromCode(attributionCode == null ? "" : attributionCode.trim());
        if (attribution == null) {
            throw new IllegalArgumentException("非法归因: " + attributionCode
                    + "（合法值 PLANNING/TOOL/ENVIRONMENT/SKILL）");
        }
        boolean updated = caseCandidateRepository.updateAttribution(id, attribution,
                note, by == null || by.isBlank() ? "unknown" : by.trim(),
                FMT.format(LocalDateTime.now()));
        return updated ? 1 : 0;
    }

    /**
     * 归因分布统计（纯函数，端点只做装配）：按四分层计数与占比。
     * 未覆盖分层以 0 计数出现（前端环形图需要全量键）。
     */
    public Map<CaseAttribution, AttributionStat> summarize(List<CaseCandidateEntity> attributed) {
        Map<CaseAttribution, AttributionStat> result = new EnumMap<>(CaseAttribution.class);
        for (CaseAttribution a : CaseAttribution.values()) {
            result.put(a, new AttributionStat(0, 0.0));
        }
        for (CaseCandidateEntity c : attributed) {
            if (c.getAttribution() != null && result.containsKey(c.getAttribution())) {
                AttributionStat stat = result.get(c.getAttribution());
                stat.count++;
            }
        }
        int total = attributed.size();
        for (AttributionStat stat : result.values()) {
            stat.ratio = total == 0 ? 0.0 : (double) stat.count / total;
        }
        return result;
    }

    /** 端点取数：时间窗 + 来源过滤交给仓储，这里只透传（统计计算在 summarize） */
    public List<CaseCandidateEntity> loadAttributed(String startTime, String endTime, CaseSource source) {
        return caseCandidateRepository.queryAttributed(startTime, endTime, source, 2000);
    }

    /** 单层统计值（count + ratio） */
    public static class AttributionStat {
        public int count;
        public double ratio;

        public AttributionStat(int count, double ratio) {
            this.count = count;
            this.ratio = ratio;
        }
    }
}
