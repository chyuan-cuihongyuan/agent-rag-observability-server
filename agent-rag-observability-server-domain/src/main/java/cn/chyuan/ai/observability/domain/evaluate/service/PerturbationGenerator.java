package cn.chyuan.ai.observability.domain.evaluate.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扰动生成器（工单 0174 X5，借鉴 promptfoo perturbation）— 规则版三类确定性扰动：
 * ①空白规范化 ②标点剥离 ③同义词表替换（内置最小表 + 配置注入扩展）。
 * 生成语义不变的变体（原样返回副本用于重跑对比方差）。
 */
@Component
public class PerturbationGenerator {

    /** 内置同义表（可由配置覆盖/扩展） */
    private final Map<String, String> synonyms;

    public PerturbationGenerator() {
        this(Map.of("请问", "麻烦问下", "怎么", "如何", "多少钱", "价格多少"));
    }

    public PerturbationGenerator(Map<String, String> synonyms) {
        this.synonyms = synonyms == null ? Map.of() : synonyms;
    }

    /** 生成全部扰动变体（确定性顺序：空白→标点→同义），同输入恒同输出 */
    public List<String> generate(String query) {
        List<String> variants = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return variants;
        }
        String whitespace = normalizeWhitespace(query);
        if (!whitespace.equals(query)) {
            variants.add(whitespace);
        }
        String noPunct = stripPunctuation(query);
        if (!noPunct.equals(query)) {
            variants.add(noPunct);
        }
        String syn = applySynonyms(query);
        if (!syn.equals(query)) {
            variants.add(syn);
        }
        return variants;
    }

    /** 空白规范化：连续空白折叠为单空格、去首尾 */
    public String normalizeWhitespace(String q) {
        return q == null ? null : q.replaceAll("\\s+", " ").trim();
    }

    /** 标点剥离：去中英文标点 */
    public String stripPunctuation(String q) {
        return q == null ? null : q.replaceAll("[\\p{P}\\p{S}]+", "");
    }

    /** 同义词表替换（整词最长匹配，逐键顺序替换） */
    public String applySynonyms(String q) {
        if (q == null) {
            return null;
        }
        String result = q;
        Map<String, String> merged = new LinkedHashMap<>();
        synonyms.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .forEach(e -> merged.put(e.getKey(), e.getValue()));
        for (Map.Entry<String, String> e : merged.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }
}
