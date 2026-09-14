package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.EvalRecordVO;
import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.FewShotSetVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 少样本样例挖掘（工单 0324 AO2，dspy bootstrap few-shot 思想）。
 * 评测记录按分数阈值过滤 → 输入字符三元组 Jaccard 相似度去重（相似输入只留
 * 高分者）→ 得分降序取前 K → 渲染少样本段。domain 纯函数。
 */
public class FewShotMiner {

    private final double scoreThreshold;
    private final double diversityThreshold;

    public FewShotMiner(double scoreThreshold, double diversityThreshold) {
        if (scoreThreshold < 0 || scoreThreshold > 100) {
            throw new IllegalArgumentException("分数阈值应在 [0,100]");
        }
        if (diversityThreshold < 0 || diversityThreshold > 1) {
            throw new IllegalArgumentException("多样性阈值应在 [0,1]");
        }
        this.scoreThreshold = scoreThreshold;
        this.diversityThreshold = diversityThreshold;
    }

    public FewShotSetVO mine(List<EvalRecordVO> records, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("样例上限必须为正数");
        }
        if (records == null) {
            records = List.of();
        }
        // 阈值过滤 + 得分降序（同分按输入稳定序）
        List<EvalRecordVO> eligible = new ArrayList<>(records.stream()
                .filter(r -> r != null && r.getScore() >= scoreThreshold)
                .toList());
        eligible.sort(Comparator.comparingInt(EvalRecordVO::getScore).reversed()
                .thenComparing(EvalRecordVO::getInput, Comparator.nullsFirst(Comparator.naturalOrder())));
        // 相似去重：与已选集合任一样例 Jaccard 超阈值即淘汰
        List<EvalRecordVO> selected = new ArrayList<>();
        List<Set<String>> selectedGrams = new ArrayList<>();
        int dropped = 0;
        for (EvalRecordVO record : eligible) {
            if (selected.size() >= k) {
                dropped++;
                continue;
            }
            Set<String> grams = trigrams(normalize(record.getInput()));
            boolean duplicate = false;
            for (Set<String> existing : selectedGrams) {
                if (jaccard(grams, existing) > diversityThreshold) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                dropped++;
                continue;
            }
            selected.add(record);
            selectedGrams.add(grams);
        }
        StringBuilder rendered = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0) {
                rendered.append('\n');
            }
            rendered.append("示例").append(i + 1).append("：输入 ")
                    .append(selected.get(i).getInput())
                    .append(" → 输出 ").append(selected.get(i).getOutput());
        }
        return FewShotSetVO.builder()
                .samples(selected)
                .rendered(rendered.toString())
                .dropped(dropped)
                .build();
    }

    /** 归一：去空白+小写 */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "").toLowerCase();
    }

    /** 字符三元组集合 */
    static Set<String> trigrams(String text) {
        Set<String> grams = new LinkedHashSet<>();
        for (int i = 0; i + 3 <= text.length(); i++) {
            grams.add(text.substring(i, i + 3));
        }
        return grams;
    }

    /** Jaccard：|∩|/|∪|（双方皆空视为完全相同 1.0） */
    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        long union = a.size() + b.size();
        long inter = a.stream().filter(b::contains).count();
        union -= inter;
        return union == 0 ? 1.0 : (double) inter / union;
    }
}
