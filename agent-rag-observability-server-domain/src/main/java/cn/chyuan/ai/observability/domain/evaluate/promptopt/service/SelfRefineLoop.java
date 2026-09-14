package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.RefineResultVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 自我改进循环（工单 0328 AO6，instructor 校验重试思想）。
 * 生成（错误回喂构造修订提示）→结构化校验器自评（错误清单带路径）→
 * 通过即停；校验器异常按"校验失败+兜底错误"处理；重试上限后带最后错误失败。
 * domain 纯函数编排。
 */
public class SelfRefineLoop {

    /** 生成端口：输入 + 上次错误反馈（首轮 null）→ 输出 */
    public interface GeneratePort {
        String generate(String input, List<RefineResultVO.ValidationErrorVO> feedback);
    }

    /** 校验端口：输出 → 错误清单（空=通过）；异常视为校验失败 */
    public interface Validator {
        List<RefineResultVO.ValidationErrorVO> validate(String output);
    }

    private final int maxAttempts;

    public SelfRefineLoop(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("重试上限必须为正数");
        }
        this.maxAttempts = maxAttempts;
    }

    public RefineResultVO run(String input, GeneratePort generatePort, Validator validator) {
        if (generatePort == null || validator == null) {
            throw new IllegalArgumentException("生成与校验端口不能为空");
        }
        List<RefineResultVO.ValidationErrorVO> feedback = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String output;
            try {
                output = generatePort.generate(input, feedback);
            } catch (RuntimeException e) {
                output = null;
            }
            List<RefineResultVO.ValidationErrorVO> errors = safeValidate(validator, output);
            if (errors.isEmpty()) {
                return RefineResultVO.builder()
                        .output(output)
                        .passed(true)
                        .attempts(attempt)
                        .lastErrors(List.of())
                        .build();
            }
            feedback = errors;
            if (attempt == maxAttempts) {
                return RefineResultVO.builder()
                        .output(output)
                        .passed(false)
                        .attempts(attempt)
                        .lastErrors(errors)
                        .build();
            }
        }
        throw new IllegalStateException("不可达");
    }

    private List<RefineResultVO.ValidationErrorVO> safeValidate(Validator validator, String output) {
        try {
            List<RefineResultVO.ValidationErrorVO> errors = validator.validate(output);
            return errors == null ? List.of() : errors;
        } catch (RuntimeException e) {
            return List.of(RefineResultVO.ValidationErrorVO.builder()
                    .path("$")
                    .message("校验器异常: " + e.getMessage())
                    .build());
        }
    }

    /** 错误清单 → 回喂反馈文本（生成端口可见） */
    public static String renderFeedback(List<RefineResultVO.ValidationErrorVO> errors) {
        return errors.stream()
                .map(e -> e.getPath() + ": " + e.getMessage())
                .collect(Collectors.joining("; "));
    }
}
