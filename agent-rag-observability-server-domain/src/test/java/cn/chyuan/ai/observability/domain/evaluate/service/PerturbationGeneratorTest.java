package cn.chyuan.ai.observability.domain.evaluate.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 扰动生成器单元测试（工单 0174 X5）— 三类规则、确定性、语义保持断言样本。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("扰动生成器测试")
class PerturbationGeneratorTest {

    private final PerturbationGenerator generator = new PerturbationGenerator();

    @Test
    @DisplayName("确定性 — 同输入恒同输出")
    public void testDeterministic() {
        assertEquals(generator.generate("请问柴油怎么卖？"), generator.generate("请问柴油怎么卖？"));
    }

    @Test
    @DisplayName("三类规则 — 空白规范化/标点剥离/同义替换各自生效")
    public void testRules() {
        // 空白规范化
        assertTrue(generator.generate("加  油卡\n办理").contains("加 油卡 办理"));
        // 标点剥离
        assertTrue(generator.generate("请问柴油怎么卖？").contains("请问柴油怎么卖"));
        // 同义替换（内置表：请问→麻烦问下）
        assertTrue(generator.generate("请问一下，卡多少钱？").contains("麻烦问下一下，卡价格多少？"));
    }

    @Test
    @DisplayName("语义不变样本 — 规则扰动后仍含原查询关键实体")
    public void testSemanticSamples() {
        for (String variant : generator.generate("加油卡余额怎么查询？")) {
            assertTrue(variant.contains("加油卡"), "扰动不应丢失关键实体: " + variant);
            assertTrue(variant.contains("查询"), "扰动不应丢失关键动作: " + variant);
        }
    }

    @Test
    @DisplayName("空串与无扰动空间 — 返回空变体列表")
    public void testNoVariants() {
        assertTrue(generator.generate("").isEmpty());
        assertTrue(generator.generate(null).isEmpty());
        assertTrue(generator.generate("简单查询").isEmpty());
    }

    @Test
    @DisplayName("配置扩展同义表 — 最长键优先")
    public void testCustomSynonyms() {
        PerturbationGenerator custom = new PerturbationGenerator(Map.of(
                "营业时间", "开门时间", "营业", "开门"));
        String out = custom.applySynonyms("你们营业时间到几点");
        assertEquals("你们开门时间到几点", out);
        assertTrue(custom.generate("你们营业时间到几点").contains(out));
    }
}
