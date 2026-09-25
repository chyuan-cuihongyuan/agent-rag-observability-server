package cn.chyuan.ai.observability.domain.fiberkernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fiber 节点（工单 0785 CO1，react Fiber 思想）。
 * child·sibling·return(parent) 链与 alternate 双缓冲对位/副作用 flags/lane 优先级。
 */
public final class Fiber {

    public static final int PLACEMENT = 1;
    public static final int UPDATE = 2;
    public static final int DELETION = 4;

    public final String type;
    public final String key;
    public Map<String, Object> props;
    public Fiber child;
    public Fiber sibling;
    public Fiber parent;
    public Fiber alternate;
    public int flags;
    public int lane = Integer.MAX_VALUE;
    public final List<Object> memoizedState = new ArrayList<>();

    public Fiber(String type, String key, Map<String, Object> props) {
        this.type = type;
        this.key = key;
        this.props = props == null ? Map.of() : Map.copyOf(props);
    }

    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    public void setFlag(int flag) {
        flags |= flag;
    }

    /** flags 合并查询：命中任一 */
    public static boolean any(Fiber f, int mask) {
        return (f.flags & mask) != 0;
    }

    /** 深度优先先序遍历（父先于子，兄弟保序） */
    public static void walk(Fiber root, java.util.function.Consumer<Fiber> visitor) {
        if (root == null) {
            return;
        }
        visitor.accept(root);
        walk(root.child, visitor);
        walk(root.sibling, visitor);
    }

    /** 先序展开为列表 */
    public static List<Fiber> preOrder(Fiber root) {
        List<Fiber> out = new ArrayList<>();
        walk(root, out::add);
        return out;
    }
}
