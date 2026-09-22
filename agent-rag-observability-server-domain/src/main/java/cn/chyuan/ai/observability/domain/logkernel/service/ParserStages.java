package cn.chyuan.ai.observability.domain.logkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管道解析阶段（工单 0575 BQ3，loki pipeline stages 思想）。
 * json 解析器提取扁平字段/regexp 命名捕获/pattern 模式（&lt;field&gt; 占位）解析/
 * 解析失败行保留原行（提取字段为空）/提取字段可供后续阶段。
 */
public final class ParserStages {

    /** 解析结果：原行 + 提取字段（失败行 extracted 为空） */
    public record ParsedLine(String line, Map<String, String> extracted) {
    }

    /** 阶段接口：解析成功返回字段集，失败返回空 Map（行保留） */
    @FunctionalInterface
    public interface Stage {
        Map<String, String> parse(String line);
    }

    /** json 扁平字段提取（"k":"v" 与 "k":123；非 json 或解析失败空 Map） */
    public static Stage json() {
        return line -> {
            if (line == null || !line.startsWith("{") || !line.endsWith("}")) {
                return Map.of();
            }
            Map<String, String> fields = new LinkedHashMap<>();
            Matcher matcher = Pattern
                    .compile("\"([^\"]+)\"\\s*:\\s*(\"([^\"]*)\"|-?\\d+(?:\\.\\d+)?)")
                    .matcher(line);
            while (matcher.find()) {
                String key = matcher.group(1);
                String raw = matcher.group(2);
                fields.put(key, raw.startsWith("\"") ? raw.substring(1, raw.length() - 1) : raw);
            }
            return fields;
        };
    }

    /** regexp 命名捕获（须含 (?&lt;name&gt;...) 组；不匹配空 Map） */
    public static Stage regexp(String regex) {
        Pattern pattern = Pattern.compile(regex);
        return line -> {
            Matcher matcher = pattern.matcher(line == null ? "" : line);
            if (!matcher.find()) {
                return Map.of();
            }
            Map<String, String> fields = new LinkedHashMap<>();
            for (String name : new String[]{}) {
                fields.put(name, "");
            }
            matcher.namedGroups().forEach((name, group) -> {
                String value = matcher.group(group);
                if (value != null) {
                    fields.put(name, value);
                }
            });
            return fields;
        };
    }

    /** pattern 模式：空格分隔字面量与 &lt;field&gt; 占位（字面量不一致空 Map） */
    public static Stage pattern(String pattern) {
        String[] tokens = pattern.trim().split("\\s+");
        return line -> {
            if (line == null) {
                return Map.of();
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < tokens.length) {
                return Map.of();
            }
            Map<String, String> fields = new LinkedHashMap<>();
            int partIndex = 0;
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                if (token.startsWith("<") && token.endsWith(">")) {
                    String value = i == tokens.length - 1
                            ? String.join(" ", java.util.Arrays.copyOfRange(parts, partIndex, parts.length))
                            : parts[partIndex];
                    fields.put(token.substring(1, token.length() - 1), value);
                    partIndex += i == tokens.length - 1 ? parts.length - partIndex : 1;
                } else if (!token.equals(parts[partIndex])) {
                    return Map.of();
                } else {
                    partIndex++;
                }
            }
            return fields;
        };
    }

    /** 多阶段顺序组合：后续阶段在先前行与累计字段上提取 */
    public List<ParsedLine> run(List<String> lines, List<Stage> stages) {
        List<ParsedLine> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            Map<String, String> extracted = new LinkedHashMap<>();
            for (Stage stage : stages) {
                stage.parse(line).forEach(extracted::putIfAbsent);
            }
            out.add(new ParsedLine(line, java.util.Collections.unmodifiableMap(extracted)));
        }
        return List.copyOf(out);
    }
}
