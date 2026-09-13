package cn.chyuan.ai.observability.domain.notify.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知模板渲染（工单 0297 AL5，借鉴 Listmonk templates）—
 * {{var}} 占位替换 + 缺失变量显式报错清单 + HTML 转义防注入 + 模板存档版本/按事件类型绑定。
 * 纯函数渲染 + 内存注册表。
 */
public class TemplateRenderer {

    /** 渲染异常（缺失变量/非法模板统一出口） */
    public static class RenderException extends IllegalArgumentException {
        public RenderException(String message) {
            super(message);
        }
    }

    /** 模板存档 */
    public record Template(String name, int version, String eventType, String body, String operator) {

        public Template {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("模板名不能为空");
            }
            if (body == null || body.isBlank()) {
                throw new IllegalArgumentException("模板内容不能为空");
            }
            eventType = eventType == null || eventType.isBlank() ? "*" : eventType;
        }
    }

    private final Map<String, Template> templates = new LinkedHashMap<>();
    private final Map<String, Integer> versions = new LinkedHashMap<>();

    /** 保存模板（版本递增；同事件类型重复绑定后者覆盖） */
    public synchronized Template save(Template template) {
        int version = versions.merge(template.name(), 1, Integer::sum);
        Template withVersion = new Template(template.name(), version, template.eventType(),
                template.body(), template.operator());
        templates.put(template.name(), withVersion);
        return withVersion;
    }

    /** 按事件类型取默认模板（精确绑定优先，其次通配 *） */
    public Template defaultFor(String eventType) {
        Template bound = templates.values().stream()
                .filter(t -> t.eventType().equals(eventType))
                .reduce((a, b) -> b)
                .orElse(null);
        if (bound != null) {
            return bound;
        }
        return templates.values().stream()
                .filter(t -> "*".equals(t.eventType()))
                .reduce((a, b) -> b)
                .orElse(null);
    }

    /**
     * 渲染：{{var}} 占位替换（值 HTML 转义），缺失变量抛 RenderException 并列出清单；
     * 同一变量多次出现只报一次。
     */
    public String render(String templateBody, Map<String, String> variables) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*)}}").matcher(templateBody);
        List<String> missing = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            out.append(templateBody, last, matcher.start());
            String key = matcher.group(1);
            String value = variables == null ? null : variables.get(key);
            if (value == null) {
                if (!missing.contains(key)) {
                    missing.add(key);
                }
                out.append(matcher.group(0));
            } else {
                out.append(escape(value));
            }
            last = matcher.end();
        }
        out.append(templateBody.substring(last));
        if (!missing.isEmpty()) {
            throw new RenderException("缺失变量: " + String.join(", ", missing));
        }
        return out.toString();
    }

    /** HTML 转义（防注入） */
    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
