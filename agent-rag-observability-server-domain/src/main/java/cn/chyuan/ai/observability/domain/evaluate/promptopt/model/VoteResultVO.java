package cn.chyuan.ai.observability.domain.evaluate.promptopt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 自洽投票结果值对象（AO5：归一票仓 + 胜出答案 + 一致率）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoteResultVO {

    /** 胜出答案（归一后的原始写法，全失败为 null） */
    private String answer;

    /** 一致率：最高票数 / 有效采样数（无有效采样为 0） */
    private double agreementRate;

    /** 票仓：归一键 → 原始写法列表（首现序） */
    private Map<String, List<String>> tally;

    /** 失败采样（null/空白）数 */
    private int failedSamples;

    /** 有效采样数 */
    private int validSamples;
}
