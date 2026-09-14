package cn.chyuan.ai.observability.domain.evaluate.promptopt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自我改进循环结果值对象（AO6：instructor 校验重试思想）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefineResultVO {

    /** 最终输出（失败为最后一次输出） */
    private String output;

    /** 是否通过全部校验 */
    private boolean passed;

    /** 实际尝试次数 */
    private int attempts;

    /** 最后一次校验错误清单（通过为空） */
    private List<ValidationErrorVO> lastErrors;

    /** 校验错误值对象：JSON 路径 + 错误消息 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ValidationErrorVO {
        private String path;
        private String message;
    }
}
