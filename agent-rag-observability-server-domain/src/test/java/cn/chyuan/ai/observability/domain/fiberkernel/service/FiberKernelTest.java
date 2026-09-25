package cn.chyuan.ai.observability.domain.fiberkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 视图协调内核测试（工单 0785-0792 CO1-CO8，react Fiber 思想）。
 * element·fiber 模型/单子与列表 key diff/flags 与副作用序/lane 调度/双缓冲/hook 链/端口与图元树联动。
 */
class FiberKernelTest {

    private static Element li(String key) {
        return new Element("li", key, Map.of("label", key), List.of());
    }

    @Test
    void elementAndFiberModel() {
        Element tree = new Element("div", null, Map.of("cls", "x"),
                List.of(new Element("span", "a", Map.of(), List.of()), Element.leaf("br", Map.of())));
        assertEquals(2, tree.children().size());
        assertThrows(IllegalArgumentException.class,
                () -> new Element("div", null, Map.of(),
                        List.of(new Element("i", "k", Map.of(), List.of()),
                                new Element("b", "k", Map.of(), List.of()))), "兄弟重复 key 拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new Element("", null, Map.of(), List.of()), "type 缺失拒绝");
        Fiber root = new Reconciler().mount(tree);
        assertEquals("span", root.child.type);
        assertEquals("br", root.child.sibling.type);
        assertSame(root, root.child.parent, "return 链指回父");
        assertNull(root.child.child);
    }

    @Test
    void mountAllPlacement() {
        Fiber root = new Reconciler().mount(new Element("div", null, Map.of(),
                List.of(new Element("span", "a", Map.of(), List.of()),
                        new Element("span", "b", Map.of(), List.of()))));
        List<Fiber> all = Fiber.preOrder(root);
        assertEquals(3, all.size());
        all.forEach(f -> assertTrue(f.hasFlag(Fiber.PLACEMENT), "挂载全 PLACEMENT"));
    }

    @Test
    void singleChildDiff() {
        Reconciler r = new Reconciler();
        Fiber current = r.mount(new Element("div", null, Map.of("v", 1),
                List.of(Element.leaf("span", Map.of("t", 1)))));
        // 同 props 重复 reconcile → 无 UPDATE（幂等）
        Reconciler.Result same = r.diff(current, new Element("div", null, Map.of("v", 1),
                List.of(Element.leaf("span", Map.of("t", 1)))));
        assertTrue(same.deletions().isEmpty());
        assertTrue(Fiber.preOrder(same.wip()).stream().allMatch(f -> f.flags == 0), "同 props 无副作用");
        // props 变更 → 仅该节点 UPDATE
        Reconciler.Result changed = r.diff(current, new Element("div", null, Map.of("v", 2),
                List.of(Element.leaf("span", Map.of("t", 1)))));
        assertTrue(changed.wip().hasFlag(Fiber.UPDATE));
        assertFalse(changed.wip().child.hasFlag(Fiber.UPDATE));
        assertSame(current, changed.wip().alternate, "alternate 对位旧树");
        // type 变更 → 旧入删除清单，新全 PLACEMENT
        Reconciler.Result replaced = r.diff(current, new Element("main", null, Map.of(), List.of()));
        assertEquals(List.of(current), replaced.deletions(), "异 type 旧子树删除");
        assertTrue(Fiber.preOrder(replaced.wip()).stream().allMatch(f -> f.hasFlag(Fiber.PLACEMENT)));
    }

    @Test
    void keyedListDiff() {
        Reconciler r = new Reconciler();
        Fiber current = r.mount(new Element("ul", null, Map.of(), List.of(li("k1"), li("k2"), li("k3"))));
        Reconciler.Result reorder = r.diff(current,
                new Element("ul", null, Map.of(), List.of(li("k3"), li("k1"), li("k2"))));
        assertTrue(reorder.deletions().isEmpty(), "重排全复用");
        assertTrue(Fiber.preOrder(reorder.wip()).stream().allMatch(f -> f.flags == 0), "重排无副作用");
        Reconciler.Result added = r.diff(current,
                new Element("ul", null, Map.of(), List.of(li("k3"), li("k1"), li("k2"), li("k4"))));
        assertTrue(added.deletions().isEmpty());
        Fiber k4 = Fiber.preOrder(added.wip()).stream().filter(f -> "k4".equals(f.key)).findFirst().get();
        assertTrue(k4.hasFlag(Fiber.PLACEMENT), "新增 PLACEMENT");
        Reconciler.Result removed = r.diff(current,
                new Element("ul", null, Map.of(), List.of(li("k3"), li("k1"))));
        assertEquals(1, removed.deletions().size());
        assertEquals("k2", removed.deletions().get(0).key, "未复用回收");
        assertThrows(IllegalArgumentException.class,
                () -> new Element("ul", null, Map.of(), List.of(li("x"), li("x"))), "新树重复 key 拒绝");
    }

    @Test
    void keylessPositionalFallback() {
        Reconciler r = new Reconciler();
        Fiber current = r.mount(new Element("div", null, Map.of(),
                List.of(Element.leaf("span", Map.of()), Element.leaf("div", Map.of()))));
        Reconciler.Result swapped = r.diff(current, new Element("div", null, Map.of(),
                List.of(Element.leaf("div", Map.of()), Element.leaf("span", Map.of()))));
        assertEquals(2, swapped.deletions().size(), "无 key 按位对齐：位错型异全替换");
        assertEquals(List.of("span", "div"), swapped.deletions().stream().map(f -> f.type).toList());
    }

    @Test
    void effectCollectionOrder() {
        Reconciler r = new Reconciler();
        Fiber current = r.mount(new Element("div", null, Map.of("v", 1),
                List.of(li("k1"), Element.leaf("span", Map.of()))));
        Reconciler.Result result = r.diff(current, new Element("main", null, Map.of("v", 2), List.of()));
        List<Fiber> effects = r.collectEffects(result);
        assertEquals(current, effects.get(0), "删除先行");
        assertTrue(effects.get(1).hasFlag(Fiber.PLACEMENT), "其后先序 placement");
    }

    @Test
    void lanesScheduling() {
        Lanes lanes = new Lanes();
        List<Lanes.Task> ordered = lanes.schedule(List.of(
                new Lanes.Task("a", 2), new Lanes.Task("b", 0), new Lanes.Task("c", 2), new Lanes.Task("d", 1)));
        assertEquals(List.of("b", "d", "a", "c"), ordered.stream().map(Lanes.Task::id).toList(),
                "lane 升序且同 lane 保插入序");
        int mask = lanes.merge(3, 1, 5);
        assertEquals(1, lanes.highest(mask), "最低 lane 即最高优先级");
        assertTrue(lanes.contains(mask, 5));
        assertFalse(lanes.contains(mask, 2));
        assertThrows(IllegalArgumentException.class, () -> lanes.highest(0), "空位图拒绝");
        assertThrows(IllegalArgumentException.class, () -> lanes.merge(32), "lane 越界拒绝");
    }

    @Test
    void doubleBufferCycle() {
        DoubleBuffer db = new DoubleBuffer();
        Reconciler r = new Reconciler();
        assertNull(db.current());
        assertThrows(IllegalStateException.class, db::commit, "无渲染提交拒绝");
        Fiber wip = r.mount(new Element("div", null, Map.of(), List.of()));
        db.begin(wip);
        assertSame(wip, db.workInProgress());
        db.discard();
        assertNull(db.workInProgress(), "丢弃回滚");
        assertNull(db.current());
        Fiber wip2 = r.mount(new Element("span", null, Map.of(), List.of()));
        db.begin(wip2);
        assertSame(wip2, db.commit(), "提交转正");
        assertSame(wip2, db.current());
        assertNull(db.workInProgress());
    }

    @Test
    void hooksAlignAndQueue() {
        HookChain h = new HookChain();
        h.beginRender();
        Object first = h.useState(0, "n0");
        assertEquals("n0", first, "首渲染初值");
        h.useState(1, 10);
        h.endRender(2);
        h.set(0, "set");
        h.setFn(0, v -> v + "!");
        h.beginRender();
        Object second = h.useState(0, "n0");
        assertEquals("set!", second, "队列按序：值后函数式");
        h.useState(1, 10);
        h.endRender(2);
        h.set(0, "q1");
        h.setEager(0, "e2");
        h.beginRender();
        assertEquals("e2", h.useState(0, "x"), "有排队时 eager 退化为入队");
        h.useState(1, 10);
        h.endRender(2);
        assertThrows(IllegalStateException.class, () -> {
            h.beginRender();
            h.useState(0, "x");
            h.endRender(1);
        }, "hook 数量变化拒绝");
        HookChain fresh = new HookChain();
        assertThrows(IllegalArgumentException.class, () -> fresh.useState(1, "jump"), "索引跳位拒绝");
    }

    @Test
    void portRenderUpdateOrchestration() {
        FiberPort port = FiberPort.inMemory();
        FiberPort.RenderResult mount = port.render(new Element("div", null, Map.of(),
                List.of(Element.leaf("span", Map.of("t", 1)))));
        assertEquals(2, mount.effects().size(), "挂载全 placement 副作用");
        assertEquals(2, port.committed().child == null ? 1 : Fiber.preOrder(port.committed()).size());
        FiberPort.RenderResult update = port.update(new Element("div", null, Map.of(),
                List.of(Element.leaf("span", Map.of("t", 2)))));
        assertEquals(List.of("span"), update.effects().stream().map(f -> f.type).toList(),
                "仅 props 变更节点更新");
        assertThrows(IllegalStateException.class, () -> FiberPort.inMemory()
                .update(new Element("b", null, Map.of(), List.of())), "未挂载更新拒绝");
    }

    @Test
    void shapeTreeLinkage() {
        record Shape(String type, String key, Map<String, Object> props, List<Shape> children) {
        }
        Shape shape = new Shape("panel", "p1", Map.of("title", "m"),
                List.of(new Shape("chart", null, Map.of("kind", "line"), List.of())));
        Element element = FiberPort.fromShape(shape, Shape::type, Shape::key, Shape::props, Shape::children);
        FiberPort port = FiberPort.inMemory();
        FiberPort.RenderResult result = port.render(element);
        assertEquals(2, result.effects().size(), "图元树适配后可挂载");
        assertEquals("chart", port.committed().child.type);
    }
}
