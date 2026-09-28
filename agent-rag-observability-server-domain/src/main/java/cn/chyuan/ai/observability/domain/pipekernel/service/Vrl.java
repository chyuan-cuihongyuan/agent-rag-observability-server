package cn.chyuan.ai.observability.domain.pipekernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VRL 子集变换（工单 0979 EF4，vector 思想）。
 * 字段赋值/del 删除/if 条件丢弃三语句/未知语句拒绝。
 */
public final class Vrl {

    /** 一条已解析语句：assign / del / drop_if */
    public static final class Statement {
        enum Kind { ASSIGN, DEL, DROP_IF }

        final Kind kind;
        final String path;
        final String literal;

        private Statement(Kind kind, String path, String literal) {
            this.kind = kind;
            this.path = path;
            this.literal = literal;
        }
    }

    private final List<Statement> statements = new ArrayList<>();

    /** 编译程序：逐行解析，未知语句拒绝 */
    public static Vrl compile(List<String> program) {
        Vrl vrl = new Vrl();
        for (String raw : program) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("if ")) {
                String condition = line.substring(3, line.indexOf('{')).trim();
                if (!line.endsWith("{ drop() }")) {
                    throw new IllegalArgumentException("条件丢弃语法非法: " + line);
                }
                String[] parts = parseCondition(condition);
                vrl.statements.add(new Statement(Statement.Kind.DROP_IF, parts[0], parts[1]));
            } else if (line.startsWith("del(") && line.endsWith(")")) {
                vrl.statements.add(new Statement(Statement.Kind.DEL, path(line.substring(4, line.length() - 1)), null));
            } else if (line.contains("=") && !line.contains("==")) {
                int eq = line.indexOf('=');
                String path = path(line.substring(0, eq).trim());
                String literal = literal(line.substring(eq + 1).trim());
                vrl.statements.add(new Statement(Statement.Kind.ASSIGN, path, literal));
            } else {
                throw new IllegalArgumentException("未知语句: " + line);
            }
        }
        return vrl;
    }

    /** 应用程序：返回变换后事件，条件命中返回 null（丢弃） */
    public Event apply(Event event) {
        Map<String, String> fields = new LinkedHashMap<>(event.fields());
        for (Statement statement : statements) {
            switch (statement.kind) {
                case ASSIGN -> fields.put(statement.path, statement.literal);
                case DEL -> fields.remove(statement.path);
                case DROP_IF -> {
                    if (statement.literal.equals(fields.get(statement.path))) {
                        return null;
                    }
                }
            }
        }
        return new Event(event.type(), fields);
    }

    public int size() {
        return statements.size();
    }

    /** 路径解析：. 起头点分 */
    private static String path(String value) {
        if (!value.startsWith(".") || value.length() < 2) {
            throw new IllegalArgumentException("路径非法: " + value);
        }
        return value.substring(1);
    }

    /** 字面量解析：整数或双引号字符串 */
    private static String literal(String value) {
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        if (value.matches("-?[0-9]+")) {
            return value;
        }
        throw new IllegalArgumentException("字面量非法: " + value);
    }

    /** 条件解析：.path == literal */
    private static String[] parseCondition(String condition) {
        String[] parts = condition.split("==");
        if (parts.length != 2) {
            throw new IllegalArgumentException("条件非法: " + condition);
        }
        return new String[]{path(parts[0].trim()), literal(parts[1].trim())};
    }
}
