package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmJudgePort;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.RubricEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.DimensionVerdict;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricDimension;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricOutcome;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricVerdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rubric 执行引擎（工单 0133 R1）— 输入 answer/context/reference + Rubric：
 * <ul>
 *   <li>二元断言维度（binary=true）：渲染 judgePrompt + 追加输出格式指令 → LLM 原始输出解析为
 *       {verdict: 0|1|unknown, evidence}；解析鲁棒——非法输出/缺证据/评判不可用一律归 unknown 而非失败</li>
 *   <li>连续分维度（binary=false）：带 prompt 走 LLM 解析 0-1 分数；无 prompt 的确定性指标维度
 *       从 metricScores 注入，缺失归 unknown</li>
 *   <li>加权综合分 = Σ(可判定维度得分 × 权重) / Σ(可判定维度权重)（unknown 剔除后归一化）</li>
 *   <li>unknown 占比 = unknown 维度数 / 维度总数，作为「标准不清晰」的可度量指标反查 Rubric 质量</li>
 * </ul>
 */
@Slf4j
@Service
public class RubricExecutionEngine {

    /** 二元断言输出格式指令（追加在用户 judgePrompt 之后，约束 LLM 输出便于鲁棒解析） */
    private static final String BINARY_SUFFIX = """

            请严格按以下两行格式返回，不要输出其他内容：
            verdict: 1 或 0（1=断言通过，0=断言不通过）
            evidence: 支撑该判定的证据摘录（必填，引用输入内容）
            """;

    private static final Pattern VERDICT_LINE = Pattern.compile(
            "(?:verdict|判定|结论|断言)\\s*[：:]\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVIDENCE_LINE = Pattern.compile(
            "(?:evidence|证据|依据)\\s*[：:]\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private final ILlmJudgePort llmJudgePort;

    public RubricExecutionEngine(ILlmJudgePort llmJudgePort) {
        this.llmJudgePort = llmJudgePort;
    }

    /**
     * 执行 Rubric（LLM 维度全量评判）。
     *
     * @param rubric    评判标准（dimensions 已解析）
     * @param query     用户查询
     * @param reference 标准答案（可为空）
     * @param answer    实际答案
     * @param chunks    实际检索上下文
     */
    public RubricOutcome execute(RubricEntity rubric, String query, String reference,
                                 String answer, List<String> chunks) {
        return execute(rubric, query, reference, answer, chunks, Map.of());
    }

    /**
     * 执行 Rubric 全量维度：
     *
     * @param metricScores 确定性指标分数表（key → 0-1 分），供无 judgePrompt 的确定性维度取分
     */
    public RubricOutcome execute(RubricEntity rubric, String query, String reference,
                                 String answer, List<String> chunks, Map<String, Double> metricScores) {
        List<DimensionVerdict> verdicts = new ArrayList<>();
        List<RubricDimension> dims = rubric == null || rubric.getDimensions() == null
                ? List.of() : rubric.getDimensions();
        Map<String, String> vars = RubricPromptRenderer.buildVars(query, reference, answer, chunks);

        for (RubricDimension dim : dims) {
            verdicts.add(judgeDimension(dim, vars, metricScores == null ? Map.of() : metricScores));
        }
        return assemble(verdicts, dims);
    }

    private DimensionVerdict judgeDimension(RubricDimension dim, Map<String, String> vars,
                                            Map<String, Double> metricScores) {
        if (dim.getJudgePrompt() == null || dim.getJudgePrompt().isBlank()) {
            // 确定性指标维度：从注入的指标分数表取值
            Double score = metricScores.get(dim.getKey());
            if (score == null) {
                return unknown(dim, "确定性维度未注入指标分数");
            }
            return DimensionVerdict.builder().key(dim.getKey())
                    .verdict(score >= 0.5 ? RubricVerdict.PASS : RubricVerdict.FAIL)
                    .score(clamp(score)).evidence("确定性指标注入").build();
        }
        String output = callLlm(dim, vars);
        if (output == null || output.isBlank()) {
            return unknown(dim, "LLM 评判不可用或输出为空");
        }
        if (dim.isBinaryDim()) {
            return parseBinary(dim, output);
        }
        return parseScore(dim, output);
    }

    /** 渲染维度 prompt 并调用 LLM（binary 追加输出格式指令）；不可用返回 null */
    private String callLlm(RubricDimension dim, Map<String, String> vars) {
        String prompt = RubricPromptRenderer.render(dim.getJudgePrompt(), vars);
        if (dim.isBinaryDim()) {
            prompt = prompt + BINARY_SUFFIX;
        }
        try {
            return llmJudgePort.complete(prompt);
        } catch (Exception e) {
            log.warn("Rubric 维度评判调用失败 {}: {}", dim.getKey(), e.getMessage());
            return null;
        }
    }

    // ===== 解析（鲁棒：非法输出归 unknown 而非失败） =====

    /** 二元断言解析：verdict 行 + evidence 行均合规才判定；非法输出/缺证据 → unknown */
    DimensionVerdict parseBinary(RubricDimension dim, String output) {
        String evidence = extractEvidence(output);
        Integer code = extractVerdictCode(output);
        if (code == null) {
            return unknown(dim, "无法解析二元断言输出");
        }
        if (evidence == null) {
            return unknown(dim, "判定缺证据");
        }
        return DimensionVerdict.builder().key(dim.getKey())
                .verdict(code == 1 ? RubricVerdict.PASS : RubricVerdict.FAIL)
                .evidence(evidence).build();
    }

    /** 连续分解析：提取首个 0-1 数字；无法提取 → unknown */
    DimensionVerdict parseScore(RubricDimension dim, String output) {
        Matcher m = NUMBER.matcher(output);
        if (m.find()) {
            try {
                double score = clamp(Double.parseDouble(m.group(1)));
                return DimensionVerdict.builder().key(dim.getKey())
                        .verdict(score >= 0.5 ? RubricVerdict.PASS : RubricVerdict.FAIL)
                        .score(score).evidence(truncate(output, 200)).build();
            } catch (NumberFormatException ignored) {
                // 落入 unknown
            }
        }
        return unknown(dim, "无法解析连续分输出");
    }

    /** 提取二元断言 verdict：verdict/判定/结论 行的值映射 1|0；无法映射返回 null */
    private Integer extractVerdictCode(String output) {
        Matcher m = VERDICT_LINE.matcher(output);
        if (!m.find()) {
            return null;
        }
        return mapVerdictToken(m.group(1));
    }

    /** 1/one/pass/yes/true/通过/是 → 1；0/zero/fail/no/false/不通过/否 → 0；其余 null */
    static Integer mapVerdictToken(String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim().toLowerCase();
        return switch (t) {
            case "1", "one", "pass", "passed", "yes", "true", "通过", "是", "符合" -> 1;
            case "0", "zero", "fail", "failed", "no", "false", "不通过", "否", "不符合" -> 0;
            default -> null;
        };
    }

    private String extractEvidence(String output) {
        Matcher m = EVIDENCE_LINE.matcher(output);
        if (m.find()) {
            String evidence = m.group(1) == null ? null : m.group(1).trim();
            return (evidence == null || evidence.isEmpty()) ? null : truncate(evidence, 500);
        }
        return null;
    }

    private DimensionVerdict unknown(RubricDimension dim, String reason) {
        return DimensionVerdict.builder().key(dim.getKey())
                .verdict(RubricVerdict.UNKNOWN).evidence(reason).build();
    }

    // ===== 汇总 =====

    private RubricOutcome assemble(List<DimensionVerdict> verdicts, List<RubricDimension> dims) {
        double weightedSum = 0.0;
        double knownWeight = 0.0;
        int unknownCount = 0;
        List<RubricDimension> dimList = dims == null ? List.of() : dims;
        for (int i = 0; i < verdicts.size(); i++) {
            DimensionVerdict v = verdicts.get(i);
            double weight = i < dimList.size() && dimList.get(i).getWeight() != null
                    ? dimList.get(i).getWeight() : 0.0;
            if (v.isKnown()) {
                double score = v.getScore() != null ? v.getScore()
                        : (v.getVerdict() == RubricVerdict.PASS ? 1.0 : 0.0);
                weightedSum += score * weight;
                knownWeight += weight;
            } else {
                unknownCount++;
            }
        }
        double overall = knownWeight > WEIGHT_EPSILON ? weightedSum / knownWeight : 0.0;
        int total = verdicts.size();
        return RubricOutcome.builder()
                .dimensionVerdicts(verdicts)
                .weightedScore(round(overall))
                .unknownRatio(total == 0 ? 0.0 : round((double) unknownCount / total))
                .unknownCount(unknownCount)
                .dimensionCount(total)
                .build();
    }

    private static final double WEIGHT_EPSILON = 1e-9;

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
