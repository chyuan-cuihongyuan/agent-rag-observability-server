package cn.chyuan.ai.observability.domain.cost.service;

import cn.chyuan.ai.observability.domain.cost.adapter.repository.IModelPricingRepository;
import cn.chyuan.ai.observability.domain.cost.model.entity.ModelPricingEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本派生与聚合服务（工单 0148 U2，借鉴 LiteLLM spend 口径）。
 * <ul>
 *   <li>计价纯函数：cost = prompt/1000×输入价 + completion/1000×输出价（四舍五入到 6 位小数）</li>
 *   <li>读时派生裁定：不在 chat_result_log 落 cost 列，聚合时按计价表即时计算——免存量回填迁移，
 *       计价表修正后历史聚合自动纠正；未配置价格的模型计入 unpriced（不计入成本）</li>
 *   <li>聚合纯函数：按天（createTime 前 10 位）/按 agent 分组求和</li>
 * </ul>
 */
@Slf4j
@Service
public class CostDeriveService {

    private final IModelPricingRepository modelPricingRepository;

    public CostDeriveService(IModelPricingRepository modelPricingRepository) {
        this.modelPricingRepository = modelPricingRepository;
    }

    /**
     * 计价纯函数：pricing 为 null（未配置模型）返回 null；token 为 null 按 0 计。
     */
    public Double computeCost(String model, Integer promptTokens, Integer completionTokens,
                              ModelPricingEntity pricing) {
        if (pricing == null) {
            return null;
        }
        double prompt = (promptTokens == null ? 0 : promptTokens) / 1000.0
                * (pricing.getInputPricePer1k() == null ? 0.0 : pricing.getInputPricePer1k());
        double completion = (completionTokens == null ? 0 : completionTokens) / 1000.0
                * (pricing.getOutputPricePer1k() == null ? 0.0 : pricing.getOutputPricePer1k());
        double cost = prompt + completion;
        return Math.round(cost * 1_000_000d) / 1_000_000d;
    }

    /** 带计价表查表的便捷派生 */
    public Double derive(ChatResultEntity chat) {
        return computeCost(chat.getModelVersion(), chat.getPromptTokens(), chat.getCompletionTokens(),
                modelPricingRepository.queryByModel(chat.getModelVersion() == null ? "" : chat.getModelVersion()));
    }

    /** 按天聚合（createTime 前 10 位为天键，输入须已按时间过滤） */
    public List<Map<String, Object>> aggregateDaily(List<ChatResultEntity> chats) {
        Map<String, double[]> byDay = new LinkedHashMap<>();
        int unpriced = 0;
        for (ChatResultEntity c : chats) {
            Double cost = derive(c);
            if (cost == null) {
                unpriced++;
                continue;
            }
            String day = dayKey(c.getCreateTime());
            if (day == null) {
                continue;
            }
            byDay.computeIfAbsent(day, k -> new double[3]);
            byDay.get(day)[0] += cost;
            byDay.get(day)[1] += c.getPromptTokens() == null ? 0 : c.getPromptTokens();
            byDay.get(day)[2] += c.getCompletionTokens() == null ? 0 : c.getCompletionTokens();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, double[]> e : byDay.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", e.getKey());
            row.put("cost", round6(e.getValue()[0]));
            row.put("promptTokens", (long) e.getValue()[1]);
            row.put("completionTokens", (long) e.getValue()[2]);
            rows.add(row);
        }
        if (unpriced > 0) {
            Map<String, Object> note = new LinkedHashMap<>();
            note.put("unpricedRequests", (long) unpriced);
            rows.add(note);
        }
        return rows;
    }

    /** 按 agent 聚合（成本/请求数降序） */
    public List<Map<String, Object>> aggregateByAgent(List<ChatResultEntity> chats) {
        Map<String, double[]> byAgent = new LinkedHashMap<>();
        for (ChatResultEntity c : chats) {
            Double cost = derive(c);
            if (cost == null || c.getAgentId() == null) {
                continue;
            }
            byAgent.computeIfAbsent(c.getAgentId(), k -> new double[2]);
            byAgent.get(c.getAgentId())[0] += cost;
            byAgent.get(c.getAgentId())[1] += 1;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byAgent.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("agentId", e.getKey());
                    row.put("cost", round6(e.getValue()[0]));
                    row.put("requests", (long) e.getValue()[1]);
                    rows.add(row);
                });
        return rows;
    }

    /** 未配置价格的请求数（提示运营补计价表） */
    public long countUnpriced(List<ChatResultEntity> chats) {
        return chats.stream()
                .filter(c -> derive(c) == null)
                .count();
    }

    private String dayKey(String createTime) {
        return createTime == null || createTime.length() < 10 ? null : createTime.substring(0, 10);
    }

    private double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }
}
