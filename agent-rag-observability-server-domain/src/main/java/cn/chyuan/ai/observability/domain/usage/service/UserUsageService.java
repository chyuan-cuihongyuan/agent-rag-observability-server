package cn.chyuan.ai.observability.domain.usage.service;

import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户/租户使用分析服务（工单 0149 U3，借鉴 Langfuse users）—
 * 按维度聚合调用量/失败率/平均耗时/耗时总量；聚合纯函数与端点取数解耦。
 * 口径：ownerUserId/tenantId 为空串或不存在的记录归入 "-"（匿名桶，不丢弃）。
 */
@Slf4j
@Service
public class UserUsageService {

    private static final String ANONYMOUS = "-";

    /** 用户维度 TopN：请求数降序（并列按 ownerUserId 字典序稳定排序） */
    public List<Map<String, Object>> topUsers(List<ChatResultEntity> chats, int topN) {
        Map<String, long[]> byUser = new LinkedHashMap<>(); // requests, fails, costMsSum
        for (ChatResultEntity c : chats) {
            String user = dim(c.getOwnerUserId());
            byUser.computeIfAbsent(user, k -> new long[3]);
            long[] stat = byUser.get(user);
            stat[0]++;
            if (!"SUCCESS".equals(c.getFinalStatus())) {
                stat[1]++;
            }
            stat[2] += c.getTotalCostTimeMs() == null ? 0 : c.getTotalCostTimeMs();
        }
        int n = Math.min(Math.max(topN, 1), 100);
        List<Map<String, Object>> rows = new ArrayList<>();
        byUser.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(e -> -e.getValue()[0])
                        .thenComparing(Map.Entry::getKey))
                .limit(n)
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ownerUserId", e.getKey());
                    row.put("requests", e.getValue()[0]);
                    row.put("failRate", e.getValue()[0] == 0 ? 0.0
                            : round4((double) e.getValue()[1] / e.getValue()[0]));
                    row.put("avgCostMs", e.getValue()[0] == 0 ? 0.0
                            : round4((double) e.getValue()[2] / e.getValue()[0]));
                    rows.add(row);
                });
        return rows;
    }

    /** 租户维度汇总：调用量/失败数/总耗时（按调用量降序） */
    public List<Map<String, Object>> tenantSummary(List<ChatResultEntity> chats) {
        Map<String, long[]> byTenant = new LinkedHashMap<>();
        for (ChatResultEntity c : chats) {
            String tenant = dim(c.getTenantId());
            byTenant.computeIfAbsent(tenant, k -> new long[3]);
            long[] stat = byTenant.get(tenant);
            stat[0]++;
            if (!"SUCCESS".equals(c.getFinalStatus())) {
                stat[1]++;
            }
            stat[2] += c.getTotalCostTimeMs() == null ? 0 : c.getTotalCostTimeMs();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byTenant.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(e -> -e.getValue()[0])
                        .thenComparing(Map.Entry::getKey))
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tenantId", e.getKey());
                    row.put("requests", e.getValue()[0]);
                    row.put("fails", e.getValue()[1]);
                    row.put("totalCostMs", e.getValue()[2]);
                    rows.add(row);
                });
        return rows;
    }

    private String dim(String v) {
        return v == null || v.isBlank() ? ANONYMOUS : v;
    }

    private double round4(double v) {
        return Math.round(v * 10000d) / 10000d;
    }
}
