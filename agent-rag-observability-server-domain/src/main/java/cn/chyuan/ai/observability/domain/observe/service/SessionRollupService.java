package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.adapter.repository.IAgentDecisionRepository;
import cn.chyuan.ai.observability.domain.observe.model.entity.AgentDecisionEntity;
import cn.chyuan.ai.observability.domain.observe.model.valobj.SessionRollup;
import cn.chyuan.ai.observability.domain.observe.model.valobj.SessionSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 会话聚合视图服务（工单 0147 U1，借鉴 Langfuse Sessions）—
 * 把同一 sessionId 的多轮 trace 拼成一次会话的汇总：聚合纯函数与查询装配解耦。
 */
@Slf4j
@Service
public class SessionRollupService {

    private final IAgentDecisionRepository agentDecisionRepository;

    public SessionRollupService(IAgentDecisionRepository agentDecisionRepository) {
        this.agentDecisionRepository = agentDecisionRepository;
    }

    /** 指定会话的聚合视图（会话无轨迹时返回 null） */
    public SessionRollup queryRollup(String sessionId) {
        List<AgentDecisionEntity> traces = agentDecisionRepository.queryBySessionId(sessionId, 1, 500);
        if (traces == null || traces.isEmpty()) {
            return null;
        }
        return summarize(sessionId, traces);
    }

    /** 近期会话列表（按末次活动时间降序，limit 钳制 [1,100]） */
    public List<SessionSummary> queryRecentSessions(int limit) {
        return agentDecisionRepository.queryRecentSessions(Math.min(Math.max(limit, 1), 100));
    }

    /**
     * 聚合纯函数：成败判定按 agentStatus（SUCCESS 计成功，其余计失败——含 FAIL/TIMEOUT/空值从严）；
     * 时间排序按 createTime 字典序（yyyy-MM-dd HH:mm:ss 格式天然可比较）。
     */
    public SessionRollup summarize(String sessionId, List<AgentDecisionEntity> traces) {
        List<AgentDecisionEntity> ordered = new ArrayList<>(traces);
        ordered.sort(Comparator.comparing(
                AgentDecisionEntity::getCreateTime,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        int success = 0;
        int fail = 0;
        long costSum = 0;
        boolean anyCost = false;
        Set<String> agents = new LinkedHashSet<>();
        List<String> traceIds = new ArrayList<>(ordered.size());
        for (AgentDecisionEntity t : ordered) {
            if ("SUCCESS".equals(t.getAgentStatus())) {
                success++;
            } else {
                fail++;
            }
            if (t.getCostTimeMs() != null) {
                costSum += t.getCostTimeMs();
                anyCost = true;
            }
            if (t.getAgentId() != null && !t.getAgentId().isBlank()) {
                agents.add(t.getAgentId());
            }
            traceIds.add(t.getTraceId());
        }

        return SessionRollup.builder()
                .sessionId(sessionId)
                .rounds(ordered.size())
                .firstTime(ordered.get(0).getCreateTime())
                .lastTime(ordered.get(ordered.size() - 1).getCreateTime())
                .totalCostMs(anyCost ? costSum : null)
                .successCount(success)
                .failCount(fail)
                .agents(new ArrayList<>(agents))
                .traceIds(traceIds)
                .build();
    }
}
