package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IPairwiseRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.PairwiseRecordEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Elo 评分引擎（工单 0171 X2，借鉴 Chatbot Arena）—
 * 纯函数：expected = 1/(1+10^((Rb-Ra)/400))，更新 Ra' = Ra + K×(S-Ea)；
 * 初值 1200、K=32 可配。从 pairwise 对局历史按 id 升序重放重算（幂等）。
 */
@Slf4j
@Service
public class EloRatingService {

    public static final double DEFAULT_RATING = 1200.0;

    private final IPairwiseRecordRepository pairwiseRecordRepository;

    @Value("${elo.k-factor:32}")
    private double kFactor = 32.0;

    public EloRatingService(IPairwiseRecordRepository pairwiseRecordRepository) {
        this.pairwiseRecordRepository = pairwiseRecordRepository;
    }

    /** 测试专用构造（显式 K） */
    public EloRatingService(IPairwiseRecordRepository pairwiseRecordRepository, double kFactor) {
        this.pairwiseRecordRepository = pairwiseRecordRepository;
        this.kFactor = kFactor;
    }

    /** 期望胜率纯函数 */
    public static double expectedScore(double ratingA, double ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }

    /** 单场更新纯函数：score ∈ {1=胜, 0.5=平, 0=负} */
    public static double updatedRating(double rating, double expected, double score, double kFactor) {
        return rating + kFactor * (score - expected);
    }

    /**
     * 由对局历史重算全部模型（modelVersion=taskId 口径）Elo，按记录 id 升序重放；幂等。
     *
     * @return {modelVersion: rating} 按评分降序
     */
    public Map<String, Double> recalculate() {
        List<PairwiseRecordEntity> records = pairwiseRecordRepository.queryList(null, null, 5000);
        Map<String, Double> ratings = new TreeMap<>();
        for (PairwiseRecordEntity r : records) {
            double ra = ratings.getOrDefault(r.getTaskA(), DEFAULT_RATING);
            double rb = ratings.getOrDefault(r.getTaskB(), DEFAULT_RATING);
            double ea = expectedScore(ra, rb);
            double scoreA = switch (r.getOutcome() == null ? "" : r.getOutcome()) {
                case "A_WIN" -> 1.0;
                case "B_WIN" -> 0.0;
                default -> 0.5;
            };
            double k = kFactor;
            ratings.put(r.getTaskA(), round2(updatedRating(ra, ea, scoreA, k)));
            ratings.put(r.getTaskB(), round2(updatedRating(rb, 1.0 - ea, 1.0 - scoreA, k)));
        }
        Map<String, Double> sorted = new LinkedHashMap<>();
        ratings.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /** 单场更新（公开口径，K 因子取配置） */
    public double applyUpdate(double rating, double expected, double score) {
        return round2(rating + kFactor * (score - expected));
    }

    private double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
