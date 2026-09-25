package cn.chyuan.ai.observability.domain.designkernel.service;

import java.util.List;

/**
 * 设计令牌端口（工单 0885 CZ8，tailwind 思想）。
 * generate·purge·theme 入口统一编排/与 vizkernel 数值插值比例作色阶生成形态只读联动（泛型函数不 import）/
 * token-kernel.enabled 默认关（开启才改变行为）。
 */
public interface TokenPort {

    ClassGenerator generator();

    List<ClassGenerator.Rule> generate(List<String> classNames);

    List<ClassGenerator.Rule> purge(List<ClassGenerator.Rule> rules, List<String> usedClasses);

    List<String> themeVariables(boolean dark);

    /** vizkernel 只读联动形态：数值插值函数驱动色阶生成（比例序列形状不 import vizkernel） */
    static List<String> colorRamp(int steps, java.util.function.IntFunction<String> shadeOf) {
        if (steps <= 0) {
            throw new IllegalArgumentException("色阶步数须为正");
        }
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < steps; i++) {
            out.add(shadeOf.apply(i));
        }
        return out;
    }

    static TokenPort inMemory() {
        return new InMemoryToken();
    }
}

final class InMemoryToken implements TokenPort {

    private final ClassGenerator generator = new ClassGenerator(new DesignTokens());

    @Override
    public ClassGenerator generator() {
        return generator;
    }

    @Override
    public List<ClassGenerator.Rule> generate(List<String> classNames) {
        return generator.combine(classNames);
    }

    @Override
    public List<ClassGenerator.Rule> purge(List<ClassGenerator.Rule> rules, List<String> usedClasses) {
        return new Purge().purge(rules, usedClasses);
    }

    @Override
    public List<String> themeVariables(boolean dark) {
        return new DesignTokens().themeVariables(dark);
    }
}
