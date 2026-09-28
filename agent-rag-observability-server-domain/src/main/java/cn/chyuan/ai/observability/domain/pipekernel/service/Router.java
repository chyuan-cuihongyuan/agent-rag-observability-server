package cn.chyuan.ai.observability.domain.pipekernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板路由（工单 0982 EF7，vector 思想）。
 * 路由键模板渲染/缺失变量拒绝/按键分发到车道。
 */
public final class Router {

    private final Map<String, String> templates = new LinkedHashMap<>();
    private final Map<String, List<Event>> lanes = new LinkedHashMap<>();

    /** 为 sink 登记路由模板：template 形如 lane/{{.field}} */
    public void route(String sinkId, String template) {
        if (!template.contains("{{.") && !template.matches("[A-Za-z0-9_/-]+")) {
            throw new IllegalArgumentException("模板非法: " + template);
        }
        templates.put(sinkId, template);
    }

    /** 渲染路由键：{{.field}} 替换，缺失变量拒绝 */
    public String render(String template, Event event) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            int start = template.indexOf("{{.", i);
            if (start == -1) {
                out.append(template, i, template.length());
                break;
            }
            int end = template.indexOf("}}", start);
            if (end == -1) {
                throw new IllegalArgumentException("模板未闭合: " + template);
            }
            String field = template.substring(start + 3, end);
            String value = event.field(field);
            if (value == null) {
                throw new IllegalArgumentException("模板变量缺失: " + field);
            }
            out.append(template, i, start).append(value);
            i = end + 2;
        }
        return out.toString();
    }

    /** 分发：按 sink 模板渲染键入车道 */
    public String dispatch(String sinkId, Event event) {
        String template = templates.get(sinkId);
        if (template == null) {
            template = sinkId;
        }
        String key = render(template, event);
        lanes.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        return key;
    }

    /** 车道内容 */
    public List<Event> lane(String key) {
        return List.copyOf(lanes.getOrDefault(key, List.of()));
    }

    public int laneCount() {
        return lanes.size();
    }
}
