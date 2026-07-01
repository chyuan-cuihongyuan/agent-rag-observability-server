package cn.chyuan.ai.observability.domain.observe.service;

import cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity;
import cn.chyuan.ai.observability.domain.observe.model.entity.RagRetrievalEntity;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Trace 在线质量评分计算器 — 从 trace 已有字段实时派生质量分，不依赖 LLM、不落库。
 * <p>
 * 用于 trace 详情页「质量评分」卡片，给用户「这条 trace 质量好坏」的直观判断。
 * 精确评分（LLM-as-Judge）留给离线 eval 模块；这里追求零成本、毫秒级、启发式合理。
 * <p>
 * 派生维度：
 * <ul>
 *   <li>检索质量 retrievalQuality：基于 rerank_scores 均值/top1 + 召回数 + 空检索惩罚</li>
 *   <li>答案忠实度 faithfulness：答案非空 + 长度合理 + 有来源文档支撑（启发式）</li>
 *   <li>答案相关性 answerRelevance：query 与 answer 的词面重叠度（Jaccard 变体）</li>
 * </ul>
 */
@Slf4j
@Service
public class TraceQualityCalculator {

    /**
     * 计算 trace 在线质量评分。
     *
     * @param retrieval RAG 检索日志（可为空）
     * @param chat      聊天结果（可为空）
     * @return 三维评分（0-1，null 表示该维度无法评估）
     */
    public TraceQuality compute(RagRetrievalEntity retrieval, ChatResultEntity chat) {
        Double retrievalQuality = retrieval == null ? null : computeRetrievalQuality(retrieval);
        Double faithfulness = chat == null ? null : computeFaithfulness(chat, retrieval);
        Double answerRelevance = chat == null ? null : computeAnswerRelevance(chat);
        return new TraceQuality(retrievalQuality, faithfulness, answerRelevance);
    }

    /**
     * 检索质量分（0-1，null 表示无法评估）：
     * <ul>
     *   <li>空检索 → 0.0</li>
     *   <li>rerankScores 缺失时回退从 sourceDocs 抽 score（应对上游漏记 rerankScores 的历史数据）</li>
     *   <li>仍无任何分数信号 → null（不造假默认值，前端显示 "-"）</li>
     *   <li>有分数 → rerank top1/均值 权重 0.6 + 召回充足度 权重 0.4</li>
     * </ul>
     */
    private Double computeRetrievalQuality(RagRetrievalEntity r) {
        // 空检索 = 质量最差
        if (r.getEmptyRetrieval() != null && r.getEmptyRetrieval() == 1) {
            return 0.0;
        }

        // 解析 rerankScores；为空时回退从 sourceDocs 抽 score（应对上游漏记 rerankScores 的历史数据）
        List<Double> scores = parseScoreList(r.getRerankScores());
        if (scores.isEmpty()) {
            scores = parseScoresFromSourceDocs(r.getSourceDocs());
        }
        // 无任何分数信号：无法评估，返回 null 而非默认 0.5 拼出的假分
        if (scores.isEmpty()) {
            return null;
        }

        double avg = scores.stream().mapToDouble(d -> d).average().orElse(0.5);
        double top1 = scores.stream().mapToDouble(d -> d).max().orElse(0.5);
        // top1 权重高（最相关的那条最重要）
        double scorePart = clamp(top1 * 0.6 + avg * 0.4);

        // 召回充足度
        int topK = r.getRetrievalTopk() == null || r.getRetrievalTopk() <= 0 ? 5 : r.getRetrievalTopk();
        int count = r.getRetrievalCount() == null ? 0 : r.getRetrievalCount();
        double sufficiencyPart = count > 0 ? clamp((double) Math.min(count, topK) / topK) : 0.0;

        return clamp(scorePart * 0.6 + sufficiencyPart * 0.4);
    }

    /**
     * 答案忠实度（0-1，启发式）：
     * - 答案为空或失败 → 0.0
     * - 有来源文档支撑（sourceDocs 非空）+ 答案长度合理 → 高分
     * - 无来源支撑但有答案 → 中分（可能幻觉）
     */
    private double computeFaithfulness(ChatResultEntity chat, RagRetrievalEntity retrieval) {
        String answer = chat.getAnswer();
        // 失败或空答案
        if (answer == null || answer.isEmpty()) {
            return 0.0;
        }
        if ("FAIL".equals(chat.getFinalStatus())) {
            return 0.0;
        }

        boolean hasSource = retrieval != null
                && retrieval.getSourceDocs() != null
                && !retrieval.getSourceDocs().isEmpty()
                && !retrieval.getSourceDocs().equals("[]");

        // 答案长度合理性：太短(<10字)扣分，过长(>2000字)也轻微扣分
        int len = answer.length();
        double lenScore;
        if (len < 10) {
            lenScore = 0.4;
        } else if (len > 2000) {
            lenScore = 0.7;
        } else {
            lenScore = 0.9;
        }

        // 有来源支撑是关键信号：有来源 +0.2，无来源 -0.1（疑似幻觉）
        double sourceBonus = hasSource ? 0.15 : -0.1;
        return clamp(lenScore + sourceBonus);
    }

    /**
     * 答案相关性（0-1）：query 与 answer 的词面重叠度。
     * 复用简单分词，计算 query 关键词在 answer 中的命中率。
     */
    private double computeAnswerRelevance(ChatResultEntity chat) {
        // query 优先用 question，没有则无法评估
        String query = chat.getQuestion();
        String answer = chat.getAnswer();
        if (query == null || query.isEmpty() || answer == null || answer.isEmpty()) {
            return 0.5; // 无法评估时中性
        }

        Set<String> queryTokens = tokenize(query);
        Set<String> answerTokens = tokenize(answer);
        if (queryTokens.isEmpty()) {
            return 0.5;
        }

        // query 关键词在 answer 中的命中率
        long hit = queryTokens.stream().filter(answerTokens::contains).count();
        double hitRate = (double) hit / queryTokens.size();

        // 命中率直接映射为相关性（命中率高的答案更可能切题）
        return clamp(hitRate);
    }

    /**
     * 解析 rerank_scores JSON 数组，兼容两种格式：
     * <ul>
     *   <li>纯数字数组：{@code [0.92, 0.87]}</li>
     *   <li>对象数组：{@code [{"score":0.92}, {"score":0.87}]}（兼容 score / relevance / rerank_score 字段）</li>
     * </ul>
     */
    private List<Double> parseScoreList(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return List.of();
        }
        try {
            JSONArray array = JSON.parseArray(json);
            if (array == null || array.isEmpty()) {
                return List.of();
            }
            List<Double> list = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                Double v = extractScore(array.get(i));
                if (v != null) {
                    list.add(v);
                }
            }
            return list;
        } catch (Exception e) {
            log.debug("解析 rerankScores 失败: {}", json);
            return List.of();
        }
    }

    /** 从数组元素中抽取分数：纯数字直接取，对象取 score / relevance / rerank_score 字段。 */
    private Double extractScore(Object element) {
        if (element == null) {
            return null;
        }
        // 纯数字：[0.92, 0.87]
        if (element instanceof Number) {
            return ((Number) element).doubleValue();
        }
        // 对象：[{"score": 0.92}, {"relevance": 0.87}, ...]
        if (element instanceof JSONObject) {
            JSONObject obj = (JSONObject) element;
            for (String key : new String[]{"score", "relevance", "rerank_score", "rerankScore"}) {
                if (obj.containsKey(key)) {
                    return obj.getDouble(key);
                }
            }
        }
        return null;
    }

    /**
     * 从 sourceDocs JSON 数组中抽取 score 字段，作为 rerankScores 缺失时的兜底。
     * sourceDocs 由上游序列化为 [{chunkId, score, ...}, ...]，与 rerankScores 同源。
     * 兼容字符串数组格式（如 seed 数据 ["KB-DOC-1", ...]），非对象元素跳过。
     */
    private List<Double> parseScoresFromSourceDocs(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return List.of();
        }
        try {
            JSONArray array = JSON.parseArray(json);
            if (array == null || array.isEmpty()) {
                return List.of();
            }
            List<Double> list = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                Double v = extractScore(array.get(i));
                if (v != null) {
                    list.add(v);
                }
            }
            return list;
        } catch (Exception e) {
            log.debug("解析 sourceDocs 抽取 score 失败: {}", json);
            return List.of();
        }
    }

    /** 简单分词：中文按单字、英文按整词（与 RetrievalMetricCalculator 口径一致） */
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        String normalized = text.toLowerCase();
        List<String> words = Arrays.asList(normalized.split("[^a-z0-9\\u4e00-\\u9fa5]+"));
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (w.matches(".*[\\u4e00-\\u9fa5].*")) {
                for (int i = 0; i < w.length(); i++) {
                    char c = w.charAt(i);
                    if (c >= '一' && c <= '龥') {
                        tokens.add(String.valueOf(c));
                    }
                }
            } else {
                tokens.add(w);
            }
        }
        return tokens;
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Trace 质量评分结果 */
    public record TraceQuality(Double retrievalQuality, Double faithfulness, Double answerRelevance) {}
}
