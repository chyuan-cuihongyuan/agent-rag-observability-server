package cn.chyuan.ai.observability.domain.evaluate.promptopt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务签名值对象（AO1：dspy Signature 思想——输入/输出字段声明 + 指令模板）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskSignatureVO {

    /** 输入字段表 */
    private List<FieldDef> inputFields;

    /** 输出字段表 */
    private List<FieldDef> outputFields;

    /** 指令模板（可含 {字段名} 占位） */
    private String instruction;

    /** 字段定义：名称/类型/描述 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FieldDef {
        private String name;
        private String type;
        private String description;
    }
}
