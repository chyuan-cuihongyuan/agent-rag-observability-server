package cn.chyuan.ai.observability.api.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardDTO {
    private Overview overview;
    private List<Map<String, Object>> trendData;
    private List<Map<String, Object>> branchDistribution;
    private List<Map<String, Object>> toolUsage;
    private List<Map<String, Object>> errorRanking;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Overview {
        private long totalRequests;
        private double successRate;
        private double avgCostTimeMs;
        private double emptyRetrievalRate;
        private double failRate;
        private long totalTokens;
    }
}
