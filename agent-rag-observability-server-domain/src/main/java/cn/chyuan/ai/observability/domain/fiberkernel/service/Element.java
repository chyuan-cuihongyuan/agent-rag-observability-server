package cn.chyuan.ai.observability.domain.fiberkernel.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Element 描述（工单 0785 CO1，react Fiber 思想）。
 * type·key·props·children 四元描述；兄弟重复 key 拒绝；props 不可变。
 */
public record Element(String type, String key, Map<String, Object> props, List<Element> children) {

    public Element {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type 缺失");
        }
        props = props == null ? Map.of() : Map.copyOf(props);
        children = children == null ? List.of() : List.copyOf(children);
        Set<String> keys = new HashSet<>();
        for (Element child : children) {
            if (child.key() != null && !keys.add(child.key())) {
                throw new IllegalArgumentException("兄弟重复 key: " + child.key());
            }
        }
    }

    public static Element of(String type, List<Element> children) {
        return new Element(type, null, Map.of(), children);
    }

    public static Element leaf(String type, Map<String, Object> props) {
        return new Element(type, null, props, List.of());
    }
}
