package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IAgentDecisionRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IRagRetrievalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class DashboardService {

    private final IAgentDecisionRepository agentDecisionRepository;
    private final IRagRetrievalRepository ragRetrievalRepository;
    private final IChatResultRepository chatResultRepository;

    public DashboardService(IAgentDecisionRepository agentDecisionRepository,
                            IRagRetrievalRepository ragRetrievalRepository,
                            IChatResultRepository chatResultRepository) {
        this.agentDecisionRepository = agentDecisionRepository;
        this.ragRetrievalRepository = ragRetrievalRepository;
        this.chatResultRepository = chatResultRepository;
    }

    public long countRequests(String startTime, String endTime) {
        return agentDecisionRepository.countByCondition(Map.of("startTime", startTime, "endTime", endTime));
    }

    public long countFails(String startTime, String endTime) {
        return agentDecisionRepository.countByCondition(Map.of("agentStatus", "FAIL", "startTime", startTime, "endTime", endTime));
    }

    public double avgCostTime(String startTime, String endTime) {
        return chatResultRepository.avgCostTime(startTime, endTime);
    }

    public double emptyRetrievalRate(String startTime, String endTime) {
        try {
            List<Map<String, Object>> stats = ragRetrievalRepository.statEmptyRetrievalRate(startTime, endTime);
            if (stats == null || stats.isEmpty()) {
                return 0.0;
            }
            long emptyCount = 0;
            long totalCount = 0;
            for (Map<String, Object> bucket : stats) {
                long count = Long.parseLong(bucket.getOrDefault("count", "0").toString());
                totalCount += count;
                if ("1".equals(bucket.getOrDefault("emptyRetrieval", "0").toString())) {
                    emptyCount = count;
                }
            }
            if (totalCount == 0) return 0.0;
            return Math.round((double) emptyCount / totalCount * 10000) / 100.0; // 保留两位小数
        } catch (Exception e) {
            log.warn("统计空检索率失败, startTime={}, endTime={}", startTime, endTime, e);
        }
        return 0.0;
    }

    public List<Map<String, Object>> getTrend(String startTime, String endTime, String interval) {
        return chatResultRepository.statTrend(startTime, endTime, interval);
    }

    public List<Map<String, Object>> getBranchDistribution(String startTime, String endTime) {
        return agentDecisionRepository.statByBranchType(startTime, endTime);
    }

    public List<Map<String, Object>> getToolUsage(String startTime, String endTime) {
        return agentDecisionRepository.statByToolUsage(startTime, endTime);
    }

    public List<Map<String, Object>> getErrorRanking(String startTime, String endTime) {
        return agentDecisionRepository.statByStatus(startTime, endTime);
    }
}
