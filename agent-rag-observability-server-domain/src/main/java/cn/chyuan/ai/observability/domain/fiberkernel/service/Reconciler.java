package cn.chyuan.ai.observability.domain.fiberkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 协调器（工单 0786/0787 CO2·CO3，react Fiber 思想）。
 * 挂载全 PLACEMENT/单子同 type 复用异 type 替换/props 变更 UPDATE/列表 key 映射复用与位置对齐兜底/删除回收。
 */
public final class Reconciler {

    /** 协调结果：新 workInProgress 树与待删除旧 fiber（子树根） */
    public record Result(Fiber wip, List<Fiber> deletions) {
    }

    /** 挂载：新树全节点 PLACEMENT */
    public Fiber mount(Element root) {
        return build(root, null);
    }

    /** 协调：新旧树 diff，产出带 flags 的 WIP 与删除清单；重复 reconcile 对同一输入产出一致（幂等） */
    public Result diff(Fiber current, Element newRoot) {
        List<Fiber> deletions = new ArrayList<>();
        Fiber wip = diffNode(current, newRoot, deletions);
        return new Result(wip, deletions);
    }

    private Fiber build(Element e, Fiber parent) {
        Fiber f = new Fiber(e.type(), e.key(), e.props());
        f.parent = parent;
        f.setFlag(Fiber.PLACEMENT);
        Fiber prev = null;
        for (Element c : e.children()) {
            Fiber cf = build(c, f);
            if (prev == null) {
                f.child = cf;
            } else {
                prev.sibling = cf;
            }
            prev = cf;
        }
        return f;
    }

    private Fiber diffNode(Fiber old, Element ne, List<Fiber> deletions) {
        if (old == null) {
            return build(ne, null);
        }
        if (!old.type.equals(ne.type())) {
            deletions.add(old);
            return build(ne, null);
        }
        Fiber f = new Fiber(ne.type(), ne.key(), ne.props());
        f.parent = old.parent;
        f.alternate = old;
        if (!Objects.equals(old.props, ne.props())) {
            f.setFlag(Fiber.UPDATE);
        }
        diffChildren(f, old.child, ne.children(), deletions);
        return f;
    }

    private void diffChildren(Fiber parent, Fiber oldChild, List<Element> newChildren, List<Fiber> deletions) {
        List<Fiber> olds = new ArrayList<>();
        for (Fiber c = oldChild; c != null; c = c.sibling) {
            olds.add(c);
        }
        Map<String, Fiber> byKey = new LinkedHashMap<>();
        for (Fiber c : olds) {
            if (c.key != null && byKey.put(c.key, c) != null) {
                throw new IllegalArgumentException("旧树重复 key: " + c.key);
            }
        }
        List<Fiber> remaining = new ArrayList<>(olds);
        List<Fiber> built = new ArrayList<>();
        for (int i = 0; i < newChildren.size(); i++) {
            Element e = newChildren.get(i);
            Fiber match = null;
            if (e.key() != null) {
                match = byKey.get(e.key());
            } else if (i < olds.size()) {
                Fiber cand = olds.get(i);
                if (cand.key == null && cand.type.equals(e.type()) && remaining.contains(cand)) {
                    match = cand;
                }
            }
            Fiber nf;
            if (match != null) {
                remaining.remove(match);
                nf = diffNode(match, e, deletions);
            } else {
                nf = build(e, parent);
            }
            nf.parent = parent;
            built.add(nf);
        }
        deletions.addAll(remaining);
        Fiber prev = null;
        for (Fiber f : built) {
            if (prev == null) {
                parent.child = f;
            } else {
                prev.sibling = f;
            }
            prev = f;
        }
    }

    /** 副作用收集（工单 0788 CO4）：删除先行，其后 WIP 先序（父先于子）取 placement/update */
    public List<Fiber> collectEffects(Result result) {
        List<Fiber> out = new ArrayList<>(result.deletions());
        Fiber.walk(result.wip(), f -> {
            if (f.flags != 0 && !f.hasFlag(Fiber.DELETION)) {
                out.add(f);
            }
        });
        return out;
    }
}
