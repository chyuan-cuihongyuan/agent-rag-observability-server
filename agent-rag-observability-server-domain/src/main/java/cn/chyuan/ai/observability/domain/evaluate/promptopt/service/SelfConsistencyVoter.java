package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.VoteResultVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自洽投票内核（工单 0327 AO5，self-consistency 论文思想）。
 * 同一问题 N 次采样 → 答案归一化（去空白+小写+全角折半+同义归一表）→
 * 多数投票（并列取首现序）→ 一致率。失败采样剔除并留痕。domain 纯函数。
 */
public class SelfConsistencyVoter {

    private final Map<String, String> synonymTable;

    public SelfConsistencyVoter(Map<String, String> synonymTable) {
        this.synonymTable = synonymTable == null ? Map.of() : synonymTable;
    }

    public VoteResultVO vote(List<String> samples) {
        Map<String, List<String>> tally = new LinkedHashMap<>();
        int failed = 0;
        if (samples != null) {
            for (String sample : samples) {
                if (sample == null || sample.isBlank()) {
                    failed++;
                    continue;
                }
                tally.computeIfAbsent(normalize(sample), k -> new ArrayList<>()).add(sample.trim());
            }
        }
        String winnerKey = null;
        int winnerCount = 0;
        for (Map.Entry<String, List<String>> entry : tally.entrySet()) {
            if (entry.getValue().size() > winnerCount) {
                winnerCount = entry.getValue().size();
                winnerKey = entry.getKey();
            }
        }
        int valid = samples == null ? 0 : samples.size() - failed;
        return VoteResultVO.builder()
                .answer(winnerKey == null ? null : tally.get(winnerKey).get(0))
                .agreementRate(valid == 0 ? 0.0 : (double) winnerCount / valid)
                .tally(tally)
                .failedSamples(failed)
                .validSamples(valid)
                .build();
    }

    /** 归一：去空白+小写+全角折半+同义表映射 */
    String normalize(String raw) {
        StringBuilder sb = new StringBuilder(raw.trim().length());
        for (char c : raw.trim().toLowerCase().toCharArray()) {
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c = (char) (c - 0xFEE0);
            }
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        String normalized = sb.toString();
        return synonymTable.getOrDefault(normalized, normalized);
    }
}
