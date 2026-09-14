package cn.chyuan.ai.observability.domain.evaluate.promptopt.service;

import cn.chyuan.ai.observability.domain.evaluate.promptopt.model.TaskSignatureVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签名编译器单测（工单 0323 AO1）：编译确定性/三段结构/字段校验/模板替换。
 */
class SignatureCompilerTest {

    private final SignatureCompiler compiler = new SignatureCompiler();

    private TaskSignatureVO signature() {
        return TaskSignatureVO.builder()
                .inputFields(List.of(
                        TaskSignatureVO.FieldDef.builder().name("question").type("str").description("用户问题").build(),
                        TaskSignatureVO.FieldDef.builder().name("context").type("str").description("检索上下文").build()))
                .outputFields(List.of(
                        TaskSignatureVO.FieldDef.builder().name("answer").type("str").build()))
                .instruction("基于 {question} 与上下文作答")
                .build();
    }

    @Test
    void 编译确定性与三段结构() {
        String first = compiler.compile(signature());
        String second = compiler.compile(signature());
        assertEquals(first, second, "同签名重放编译一致");
        assertTrue(first.startsWith("## 输入字段"));
        assertTrue(first.contains("## 指令"));
        assertTrue(first.contains("## 输出格式"));
        assertTrue(first.contains("question（str）：用户问题"));
        assertTrue(first.contains("- answer（str）"));
        assertTrue(first.contains("基于 {question} 与上下文作答"));
    }

    @Test
    void 模板变量替换仅限声明字段() {
        String rendered = compiler.render("问 {question}，未知 {ghost}",
                Map.of("question", "什么是RAG", "ghost", "不应替换"), signature());
        assertEquals("问 什么是RAG，未知 {ghost}", rendered);
    }

    @Test
    void 字段表校验拒绝() {
        // 重名
        TaskSignatureVO duplicate = TaskSignatureVO.builder()
                .inputFields(List.of(
                        TaskSignatureVO.FieldDef.builder().name("a").type("str").build(),
                        TaskSignatureVO.FieldDef.builder().name("a").type("str").build()))
                .outputFields(List.of(TaskSignatureVO.FieldDef.builder().name("b").type("str").build()))
                .instruction("指令").build();
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(duplicate));
        // 空名
        TaskSignatureVO blank = TaskSignatureVO.builder()
                .inputFields(List.of(TaskSignatureVO.FieldDef.builder().name(" ").type("str").build()))
                .outputFields(List.of(TaskSignatureVO.FieldDef.builder().name("b").type("str").build()))
                .instruction("指令").build();
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(blank));
        // 空输出表
        TaskSignatureVO noOutput = TaskSignatureVO.builder()
                .inputFields(List.of(TaskSignatureVO.FieldDef.builder().name("a").type("str").build()))
                .outputFields(List.of())
                .instruction("指令").build();
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(noOutput));
        // 空指令
        TaskSignatureVO noInstruction = TaskSignatureVO.builder()
                .inputFields(List.of(TaskSignatureVO.FieldDef.builder().name("a").type("str").build()))
                .outputFields(List.of(TaskSignatureVO.FieldDef.builder().name("b").type("str").build()))
                .instruction(" ").build();
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(noInstruction));
    }
}
