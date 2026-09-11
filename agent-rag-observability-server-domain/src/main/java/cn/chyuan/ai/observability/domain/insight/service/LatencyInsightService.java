package cn.chyuan.ai.observability.domain.insight.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 慢链路洞察服务（工单 0153 U7，借鉴 SkyWalking 拓扑耗时分析）—
 * 耗时分位数（p50/p95/p99 最近邻秩统计，零依赖）+ 慢 trace TopN。
 * 空集语义：分位数与均值返回 null（不造假默认值）。
 */
@Slf4j
@Service
public class LatencyInsightService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IChatResultRepository chatResultRepository;

    public LatencyInsightService(IChatResultRepository chatResultRepository) {
        this.chatResultRepository = chatResultRepository;
    }

    /** 耗时概览（days 钳制 [1,90]） */
    public Map<String, Object> latencyOverview(int days) {
        List<Integer> costs = extractCosts(loadSources(days));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("count", costs.size());
        row.put("p50", percentile(costs, 50));
        row.put("p95", percentile(costs, 95));
        row.put("p99", percentile(costs, 99));
        row.put("avg", costs.isEmpty() ? null :
                costs.stream().mapToDouble(Integer::doubleValue).average().orElse(0.0));
        return row;
    }

    /** 慢链路 TopN（按耗时降序） */
    public List<Map<String, Object>> slowTopN(int days, int topN) {
        List<ChatResultEntity> chats = loadSources(days);
        int n = Math.min(Math.max(topN, 1), 100);
        List<Map<String, Object>> rows = new ArrayList<>();
        chats.stream()
                .filter(c -> c.getTotalCostTimeMs() != null)
                .sorted(Comparator.comparingInt(ChatResultEntity::getTotalCostTimeMs).reversed())
                .limit(n)
                .forEach(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("traceId", c.getTraceId());
                    row.put("sessionId", c.getSessionId());
                    row.put("agentId", c.getAgentId());
                    row.put("totalCostTimeMs", c.getTotalCostTimeMs());
                    row.put("createTime", c.getCreateTime());
                    rows.add(row);
                });
        return rows;
    }

    /**
     * 分位数纯函数（最近邻秩统计）：index = ceil(p/100 × n) - 1，钳制 [0, n-1]；
     * 空集返回 null。p 不在 (0,100] 区间抛 IllegalArgumentException。
     */
    public static Integer percentile(List<Integer> values, double p) {
        if (p <= 0 || p > 100) {
            throw new IllegalArgumentException("p 必须在 (0,100] 区间: " + p);
        }
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.min(Math.max(index, 0), sorted.size() - 1));
    }

    private List<Integer> extractCosts(List<ChatResultEntity> chats) {
        return chats.stream()
                .map(ChatResultEntity::getTotalCostTimeMs)
                .filter(c -> c != null)
                .toList();
    }

    private List<ChatResultEntity> loadSources(int days) {
        int d = Math.min(Math.max(days, 1), 90);
        String start = FMT.format(LocalDateTime.now().minusDays(d));
        return chatResultRepository.queryCostSources(start, 5000);
    }
}
