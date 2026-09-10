package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rubric 评判维度（工单 0133 R1）。
 * <ul>
 *   <li>key：维度标识（同一 Rubric 内唯一；确定性指标 key 如 f1/mrr 不带 judgePrompt）</li>
 *   <li>label：展示名</li>
 *   <li>weight：综合分权重（同一 Rubric 内权重和必须为 1）</li>
 *   <li>judgePrompt：LLM 评判 prompt 模板，支持 {{query}}/{{reference}}/{{answer}}/{{context}}/{{numberedContext}} 占位符；为空表示确定性指标维度</li>
 *   <li>binary：true=二元断言（输出 verdict 0|1 + evidence）；false=连续分（输出 0-1 分数）</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RubricDimension {

    public static final String VAR_QUERY = "query";
    public static final String VAR_REFERENCE = "reference";
    public static final String VAR_ANSWER = "answer";
    public static final String VAR_CONTEXT = "context";
    public static final String VAR_NUMBERED_CONTEXT = "numberedContext";

    private String key;
    private String label;
    private Double weight;
    private String judgePrompt;
    /** 二元断言维度（verdict 0|1|unknown）；false 为连续分维度 */
    private Boolean binary;

    public boolean isBinaryDim() {
        return binary != null && binary;
    }

    /** 序列化为 dimensions JSON 数组中的单元素（null 字段容忍） */
    public String toJson() {
        return JSON.toJSONString(this);
    }
}
