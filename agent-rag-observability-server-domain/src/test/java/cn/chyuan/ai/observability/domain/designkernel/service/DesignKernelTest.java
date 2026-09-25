package cn.chyuan.ai.observability.domain.designkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 设计令牌内核测试（工单 0878-0885 CZ1-CZ8，tailwind 思想）。
 * 间距色阶尺度/类名三段解析与任意值/变体栈/JIT 生成未映射拒绝/purge 白名单/主题变量暗色覆盖/组合后者胜/端口色阶联动。
 */
class DesignKernelTest {

    @Test
    void spacingScaleAndColorTokens() {
        DesignTokens tokens = new DesignTokens();
        assertEquals(16, tokens.spacingPx("4"), "4px 基");
        assertEquals(2, tokens.spacingPx("0.5"), "半步");
        assertEquals(0, tokens.spacingPx("0"));
        assertEquals("#ef4444", tokens.color("red", "500"));
        assertTrue(tokens.hasColor("slate-900"));
        assertFalse(tokens.hasColor("red-999"));
        assertThrows(IllegalArgumentException.class, () -> tokens.spacingPx("-2"), "负间距拒绝");
        assertThrows(IllegalArgumentException.class, () -> tokens.color("red", "999"), "未知色阶拒绝");
        assertThrows(IllegalArgumentException.class, () -> new DesignTokens(0), "非法基拒绝");
    }

    @Test
    void classParseThreeSegments() {
        ClassGenerator gen = new ClassGenerator(new DesignTokens());
        ClassGenerator.Parsed p = gen.parse("bg-red-500");
        assertEquals("bg", p.property());
        assertEquals("red-500", p.rawValue());
        assertTrue(p.variants().isEmpty());
        ClassGenerator.Parsed arbitrary = gen.parse("w-[32px]");
        assertTrue(arbitrary.arbitrary(), "任意值语法");
        assertEquals("[32px]", arbitrary.rawValue());
        ClassGenerator.Parsed variant = gen.parse("hover:bg-red-500");
        assertEquals(List.of("hover"), variant.variants());
        ClassGenerator.Parsed nested = gen.parse("md:hover:p-4");
        assertEquals(List.of("md", "hover"), nested.variants(), "变体栈序");
        assertThrows(IllegalArgumentException.class, () -> gen.parse("frob-x"), "未知属性前缀拒绝");
        assertThrows(IllegalArgumentException.class, () -> gen.parse("nodash"), "缺属性分隔拒绝");
    }

    @Test
    void jitGenerateScaleAndArbitrary() {
        ClassGenerator gen = new ClassGenerator(new DesignTokens());
        ClassGenerator.Rule bg = gen.generate("bg-red-500");
        assertEquals(".bg-red-500", bg.selector());
        assertEquals("background-color", bg.property());
        assertEquals("#ef4444", bg.value());
        ClassGenerator.Rule pad = gen.generate("p-4");
        assertEquals("padding", pad.property());
        assertEquals("16px", pad.value());
        ClassGenerator.Rule width = gen.generate("w-[32px]");
        assertEquals("32px", width.value(), "任意值直取");
        assertTrue(width.selector().contains("\\[32px\\]"), "选择器转义");
        ClassGenerator.Rule hover = gen.generate("hover:bg-red-500");
        assertTrue(hover.selector().endsWith(":hover"), "变体伪类后缀");
        ClassGenerator.Rule nested = gen.generate("md:hover:p-4");
        assertTrue(nested.selector().contains("@md") && nested.selector().endsWith(":hover"), "变体栈前缀拼接");
        assertThrows(IllegalArgumentException.class, () -> gen.generate("bg-red-999"), "未映射色阶拒绝");
    }

    @Test
    void combineConflictLaterWins() {
        ClassGenerator gen = new ClassGenerator(new DesignTokens());
        List<ClassGenerator.Rule> combined = gen.combine(List.of("p-4", "text-red-500", "p-2"));
        assertEquals(3, combined.size(), "异选择器共存");
        assertEquals(List.of(".p-4", ".text-red-500", ".p-2"),
                combined.stream().map(ClassGenerator.Rule::selector).toList(), "顺序确定");
    }

    @Test
    void themeVariablesAndDarkOverride() {
        DesignTokens light = new DesignTokens();
        List<String> vars = light.themeVariables(false);
        assertTrue(vars.contains("--color-red-500: #ef4444;"));
        assertTrue(vars.contains("--color-slate-900: #0f172a;"));
        DesignTokens dark = new DesignTokens(4, Map.of("--color-slate-100", "#1e293b"));
        List<String> darkVars = dark.themeVariables(true);
        assertTrue(darkVars.contains("--color-slate-100: #1e293b;"), "暗色覆盖生效");
        assertTrue(darkVars.contains("--color-red-500: #ef4444;"), "未覆盖项保持");
    }

    @Test
    void themeVarReferenceInRules() {
        ClassGenerator gen = new ClassGenerator(new DesignTokens(), true);
        ClassGenerator.Rule rule = gen.generate("bg-blue-500");
        assertEquals("var(--color-blue-500)", rule.value(), "主题变量引用");
    }

    @Test
    void purgeUnusedAndAllowlist() {
        Purge purge = new Purge();
        purge.allowPrefix("dyn-");
        ClassGenerator gen = new ClassGenerator(new DesignTokens());
        List<ClassGenerator.Rule> rules = gen.combine(List.of("p-4", "bg-red-500", "p-2", "w-[32px]"));
        List<ClassGenerator.Rule> kept = purge.purge(rules, List.of("p-4", "bg-red-500"));
        assertEquals(2, kept.size(), "未用规则清除");
        assertTrue(kept.stream().noneMatch(r -> r.className().contains("p-2")));
        Purge withAllow = new Purge();
        withAllow.allowPrefix("w-");
        List<ClassGenerator.Rule> keptWithAllow = withAllow.purge(rules, List.of("p-4"));
        assertTrue(keptWithAllow.stream().anyMatch(r -> r.className().startsWith("w-")), "白名单前缀保留");
        assertTrue(purge.scanUsed(List.of("<div class=\"p-4 bg-red-500\">")).containsAll(List.of("p-4", "bg-red-500")),
                "内容扫描提取类名");
    }

    @Test
    void portOrchestrationAndRampLinkage() {
        TokenPort port = TokenPort.inMemory();
        List<ClassGenerator.Rule> rules = port.generate(List.of("p-4", "bg-red-500", "text-blue-600"));
        assertEquals(3, rules.size());
        List<ClassGenerator.Rule> kept = port.purge(rules, List.of("p-4"));
        assertEquals(1, kept.size());
        assertTrue(port.themeVariables(false).size() >= 12);
        List<String> ramp = TokenPort.colorRamp(5, i -> tokensShade(i));
        assertEquals(List.of("#100", "#108", "#110", "#118", "#120"), ramp, "vizkernel 数值插值比例驱动色阶生成");
        assertThrows(IllegalArgumentException.class, () -> TokenPort.colorRamp(0, i -> "#000"));
    }

    private static String tokensShade(int i) {
        int v = 0x100 + i * 8;
        return "#" + Integer.toHexString(v);
    }
}
