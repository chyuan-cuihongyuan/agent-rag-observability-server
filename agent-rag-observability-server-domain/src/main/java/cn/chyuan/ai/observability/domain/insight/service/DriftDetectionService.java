package cn.chyuan.ai.observability.domain.insight.service;

import cn.chyuan.ai.observability.domain.insight.model.entity.DriftEventEntity;
import cn.chyuan.ai.observability.domain.insight.adapter.repository.IDriftEventRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 嵌入分数分布漂移检测（工单 0154 U8，借鉴 Evidently/Phoenix 漂移思想）—
 * 对比本周 vs 上周窗的 rerank 分数分布（均值漂移 + 空检索率增量），超阈值记事件。
 * 默认关（drift.enabled=false）；分布统计与判定均为纯函数。
 */
@Slf4j
@Service
public class DriftDetectionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IRagRetrievalRepository ragRetrievalRepository;
    private final IDriftEventRepository driftEventRepository;

    /** 阈值默认值与 @Value 缺省一致（纯单测环境无 Spring 注入时生效） */
    @Value("${drift.mean-threshold:0.1}")
    private double meanThreshold = 0.1;

    @Value("${drift.empty-rate-threshold:0.1}")
    private double emptyRateThreshold = 0.1;

    @Value("${drift.window-days:7}")
    private int windowDays = 7;

    public DriftDetectionService(IRagRetrievalRepository ragRetrievalRepository,
                                 IDriftEventRepository driftEventRepository) {
        this.ragRetrievalRepository = ragRetrievalRepository;
        this.driftEventRepository = driftEventRepository;
    }

    /** 分布快照（纯函数）：样本数/均值/方差/空检索率（无分数样本不计入均值方差） */
    public DistributionStats distribution(List<RagRetrievalEntity> retrievals) {
        List<Double> scores = new ArrayList<>();
        int empty = 0;
        int total = retrievals.size();
        for (RagRetrievalEntity r : retrievals) {
            if (r.getEmptyRetrieval() != null && r.getEmptyRetrieval() == 1) {
                empty++;
            }
            scores.addAll(parseScores(r.getRerankScores()));
        }
        DistributionStats stats = new DistributionStats();
        stats.total = total;
        stats.sampleCount = scores.size();
        stats.emptyRetrievalRate = total == 0 ? 0.0 : (double) empty / total;
        if (!scores.isEmpty()) {
            double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = scores.stream().mapToDouble(s -> (s - mean) * (s - mean)).average().orElse(0.0);
            stats.mean = mean;
            stats.variance = variance;
        }
        return stats;
    }

    /** 漂移判定纯函数：均值漂移超阈值 或 空检索率增量超阈值 → 漂移 */
    public boolean isDrift(DistributionStats current, DistributionStats previous) {
        if (current.sampleCount < 10 || previous.sampleCount < 10) {
            // 样本不足不判定（避免小样本误报）
            return false;
        }
        double meanDrift = Math.abs(current.mean - previous.mean);
        double emptyDelta = current.emptyRetrievalRate - previous.emptyRetrievalRate;
        return meanDrift > meanThreshold || emptyDelta > emptyRateThreshold;
    }

    /** 执行一轮对比：本周窗 vs 上周窗，漂移则落事件并返回事件（无漂移返回 null） */
    public DriftEventEntity runOnce() {
        LocalDateTime now = LocalDateTime.now();
        String curStart = FMT.format(now.minusDays(windowDays));
        String prevStart = FMT.format(now.minusDays(windowDays * 2L));
        String prevEnd = FMT.format(now.minusDays(windowDays));

        DistributionStats current = distribution(ragRetrievalRepository.queryForDrift(curStart, FMT.format(now), 5000));
        DistributionStats previous = distribution(ragRetrievalRepository.queryForDrift(prevStart, prevEnd, 5000));

        if (!isDrift(current, previous)) {
            log.info("漂移检测: 无漂移 (curMean={}, prevMean={})", current.mean, previous.mean);
            return null;
        }
        DriftEventEntity event = DriftEventEntity.builder()
                .metric("rerank_mean")
                .currentValue(round6(current.mean))
                .previousValue(round6(previous.mean))
                .threshold(meanThreshold)
                .detail(JSON.toJSONString(Map.of(
                        "currentSampleCount", current.sampleCount,
                        "previousSampleCount", previous.sampleCount,
                        "currentEmptyRate", round6(current.emptyRetrievalRate),
                        "previousEmptyRate", round6(previous.emptyRetrievalRate),
                        "windowDays", windowDays)))
                .createTime(FMT.format(now))
                .build();
        driftEventRepository.save(event);
        log.warn("检测到检索分数分布漂移: curMean={} prevMean={}", current.mean, previous.mean);
        return event;
    }

    private List<Double> parseScores(String json) {
        List<Double> list = new ArrayList<>();
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return list;
        }
        try {
            for (Object o : JSON.parseArray(json)) {
                if (o instanceof Number n) {
                    list.add(n.doubleValue());
                }
            }
        } catch (Exception ignore) {
            // 非 JSON 数组格式跳过（历史数据兼容）
        }
        return list;
    }

    private double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }

    /** 分布快照值对象（均值/方差在无分数样本时为 0，sampleCount=0 表示无信号） */
    public static class DistributionStats {
        public int total;
        public int sampleCount;
        public double mean;
        public double variance;
        public double emptyRetrievalRate;
    }
}
