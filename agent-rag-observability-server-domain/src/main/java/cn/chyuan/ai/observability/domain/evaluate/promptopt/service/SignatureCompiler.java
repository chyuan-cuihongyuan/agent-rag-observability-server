package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.TaskSignatureVO;

import java.util.HashSet;
import java.util.Set;

/**
 * 签名编译器（工单 0323 AO1，dspy signature 思想）。
 * 签名 → 确定性提示骨架三段：字段说明段/指令段/输出格式段。
 * 字段表校验（重名/空名/空输出表拒绝）。domain 纯函数。
 */
public class SignatureCompiler {

    /** 编译：同签名重放结果一致（字段顺序保留） */
    public String compile(TaskSignatureVO signature) {
        validate(signature);
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 输入字段\n");
        for (TaskSignatureVO.FieldDef field : signature.getInputFields()) {
            prompt.append("- ").append(field.getName())
                    .append("（").append(field.getType()).append("）")
                    .append(field.getDescription() == null ? "" : "：" + field.getDescription())
                    .append('\n');
        }
        prompt.append("## 指令\n").append(signature.getInstruction()).append('\n');
        prompt.append("## 输出格式\n");
        for (TaskSignatureVO.FieldDef field : signature.getOutputFields()) {
            prompt.append("- ").append(field.getName())
                    .append("（").append(field.getType()).append("）\n");
        }
        return prompt.toString();
    }

    /** 模板变量替换：{字段名} → 值（未声明字段不替换） */
    public String render(String template, java.util.Map<String, String> values,
                         TaskSignatureVO signature) {
        validate(signature);
        Set<String> declared = new HashSet<>();
        signature.getInputFields().forEach(f -> declared.add(f.getName()));
        String result = template;
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            if (declared.contains(entry.getKey())) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    private void validate(TaskSignatureVO signature) {
        if (signature == null || signature.getInputFields() == null
                || signature.getOutputFields() == null
                || signature.getOutputFields().isEmpty()) {
            throw new IllegalArgumentException("签名字段表不完整（输出表必填）");
        }
        if (signature.getInstruction() == null || signature.getInstruction().isBlank()) {
            throw new IllegalArgumentException("指令不能为空");
        }
        Set<String> seen = new HashSet<>();
        for (TaskSignatureVO.FieldDef field : signature.getInputFields()) {
            requireName(field, seen);
        }
        for (TaskSignatureVO.FieldDef field : signature.getOutputFields()) {
            requireName(field, seen);
        }
    }

    private void requireName(TaskSignatureVO.FieldDef field, Set<String> seen) {
        if (field == null || field.getName() == null || field.getName().isBlank()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        if (!seen.add(field.getName())) {
            throw new IllegalArgumentException("字段重名: " + field.getName());
        }
    }
}
