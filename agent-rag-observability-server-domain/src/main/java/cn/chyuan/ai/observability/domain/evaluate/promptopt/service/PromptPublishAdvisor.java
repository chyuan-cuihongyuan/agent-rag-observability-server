package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.PublishSuggestionVO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 提示发布建议（工单 0330 AO8）。
 * 实验胜出提示 → 发布建议记录（PROPOSED→ACCEPTED/REJECTED 终态状态机，
 * 附提升幅度/数据集指纹/风险备注）+ 确定性 JSON 导出（提示/签名/基线对比）。
 * 对接网关 promptresource 的对接面为契约描述（0304-D3，不跨模块实时调用）。
 * domain 纯函数。
 */
public class PromptPublishAdvisor {

    private final Map<String, PublishSuggestionVO> suggestions = new LinkedHashMap<>();

    /** 提出建议（仅 PROPOSED 入册） */
    public PublishSuggestionVO propose(String suggestionId, String experimentId, String prompt,
                                       String baselinePrompt, double lift, String datasetFingerprint,
                                       String note, long nowMs) {
        if (suggestionId == null || suggestionId.isBlank()) {
            throw new IllegalArgumentException("建议ID不能为空");
        }
        if (suggestions.containsKey(suggestionId)) {
            throw new IllegalArgumentException("建议ID重复: " + suggestionId);
        }
        PublishSuggestionVO suggestion = PublishSuggestionVO.builder()
                .suggestionId(suggestionId)
                .experimentId(experimentId)
                .prompt(prompt)
                .baselinePrompt(baselinePrompt)
                .lift(lift)
                .datasetFingerprint(datasetFingerprint)
                .note(note)
                .status(PublishSuggestionVO.PROPOSED)
                .proposedAtMs(nowMs)
                .decidedAtMs(0)
                .build();
        suggestions.put(suggestionId, suggestion);
        return suggestion;
    }

    /** 决策：PROPOSED → ACCEPTED / REJECTED（终态不可再转，非法转移拒绝） */
    public PublishSuggestionVO decide(String suggestionId, String target, long nowMs) {
        PublishSuggestionVO suggestion = suggestions.get(suggestionId);
        if (suggestion == null) {
            throw new IllegalArgumentException("建议不存在: " + suggestionId);
        }
        boolean legal = PublishSuggestionVO.PROPOSED.equals(suggestion.getStatus())
                && (PublishSuggestionVO.ACCEPTED.equals(target) || PublishSuggestionVO.REJECTED.equals(target));
        if (!legal) {
            throw new IllegalStateException("非法状态转移: " + suggestion.getStatus() + " → " + target);
        }
        suggestion.setStatus(target);
        suggestion.setDecidedAtMs(nowMs);
        return suggestion;
    }

    /** 建议（只读快照） */
    public java.util.List<PublishSuggestionVO> list() {
        return java.util.List.copyOf(suggestions.values());
    }

    /** 确定性 JSON 导出（建议内容+基线对比字段） */
    public String export(PublishSuggestionVO suggestion) {
        if (suggestion == null) {
            throw new IllegalArgumentException("建议不能为空");
        }
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"suggestionId\":\"").append(suggestion.getSuggestionId()).append("\",")
                .append("\"experimentId\":\"").append(suggestion.getExperimentId()).append("\",")
                .append("\"prompt\":\"").append(escape(suggestion.getPrompt())).append("\",")
                .append("\"baselinePrompt\":\"").append(escape(suggestion.getBaselinePrompt())).append("\",")
                .append("\"lift\":").append(suggestion.getLift()).append(",")
                .append("\"datasetFingerprint\":\"").append(suggestion.getDatasetFingerprint()).append("\",")
                .append("\"status\":\"").append(suggestion.getStatus()).append("\",")
                .append("\"proposedAtMs\":").append(suggestion.getProposedAtMs()).append(",")
                .append("\"decidedAtMs\":").append(suggestion.getDecidedAtMs())
                .append("}");
        return json.toString();
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
