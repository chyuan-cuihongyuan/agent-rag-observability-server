package cn.chyuan.ai.observability.domain.fiberkernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 视图协调端口（工单 0792 CO8，react Fiber 思想）。
 * render·update 入口统一编排/与 vizkernel 只读联动（图元描述树经 fromShape 适配为 element 形态，泛型入参不 import）/
 * fiber-kernel.enabled 默认关（开启才改变行为）。
 */
public interface FiberPort {

    /** 渲染结果：提交后的 current 树与副作用清单 */
    record RenderResult(Fiber tree, List<Fiber> effects) {
    }

    /** 挂载渲染 */
    RenderResult render(Element root);

    /** 协调更新（对已提交树 diff） */
    RenderResult update(Element newRoot);

    /** 已提交树 */
    Fiber committed();

    /** vizkernel 只读联动形态：任意图元树形状适配为 Element（形状数据不 import vizkernel） */
    static <N> Element fromShape(N node,
                                 Function<N, String> typeOf,
                                 Function<N, String> keyOf,
                                 Function<N, Map<String, Object>> propsOf,
                                 Function<N, List<N>> childrenOf) {
        List<N> children = childrenOf.apply(node);
        List<Element> elements = new ArrayList<>();
        for (N child : children == null ? List.<N>of() : children) {
            elements.add(fromShape(child, typeOf, keyOf, propsOf, childrenOf));
        }
        return new Element(typeOf.apply(node), keyOf.apply(node), propsOf.apply(node), elements);
    }

    static FiberPort inMemory() {
        return new InMemoryFiber();
    }
}

final class InMemoryFiber implements FiberPort {

    private final Reconciler reconciler = new Reconciler();
    private final DoubleBuffer buffer = new DoubleBuffer();

    @Override
    public RenderResult render(Element root) {
        if (buffer.current() != null) {
            throw new IllegalStateException("已挂载，请用 update");
        }
        buffer.begin(reconciler.mount(root));
        return commit();
    }

    @Override
    public RenderResult update(Element newRoot) {
        if (buffer.current() == null) {
            throw new IllegalStateException("未挂载");
        }
        Reconciler.Result result = reconciler.diff(buffer.current(), newRoot);
        buffer.begin(result.wip());
        RenderResult out = new RenderResult(buffer.commit(), reconciler.collectEffects(result));
        return out;
    }

    private RenderResult commit() {
        Fiber tree = buffer.commit();
        return new RenderResult(tree, reconciler.collectEffects(new Reconciler.Result(tree, List.of())));
    }

    @Override
    public Fiber committed() {
        return buffer.current();
    }
}
