package cn.chyuan.ai.observability.domain.regexkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 正则语法解析（工单 0634 BX1 + 0636 BX3 + 0638 BX5 + 0639 BX6，ripgrep 语法面）。
 * 并置/选择 |/量词 * + ? {m,n}（贪婪与懒惰 *? +? ?? {m,n}?）/
 * 分组（捕获/命名 (?P<name>…)）/锚点 ^ $/转义成员/
 * 预定义类 \d \w \s 及否定/点号（默认除换行）/Unicode 码点
 * （中文多字节按码点匹配）/语法错误报告位置。
 */
public final class RegexParser {

    /** AST */
    public sealed interface Ast permits Alt, Cat, Rep, CharNode, ClassNode, Group, Anchor, Empty {
    }

    /** 选择 */
    public record Alt(List<Ast> branches) implements Ast {
        public Alt {
            branches = List.copyOf(branches);
        }
    }

    /** 并置 */
    public record Cat(List<Ast> parts) implements Ast {
        public Cat {
            parts = List.copyOf(parts);
        }
    }

    /** 重复：min..max（max=-1 无界）；lazy=true 懒惰 */
    public record Rep(Ast node, int min, int max, boolean lazy) implements Ast {
    }

    /** 单字符（码点） */
    public record CharNode(int codePoint) implements Ast {
    }

    /** 字符类（区间集合，可否定） */
    public record ClassNode(List<CharClasses.Range> ranges, boolean negated) implements Ast {
    }

    /** 捕获组（index 从 1；name 可空） */
    public record Group(int index, String name, Ast body) implements Ast {
    }

    /** 锚点（^ 起点 / $ 终点） */
    public record Anchor(boolean start) implements Ast {
    }

    /** 空段 */
    public record Empty() implements Ast {
    }

    private final String pattern;
    private int pos;
    private int groupCount;

    private RegexParser(String pattern) {
        this.pattern = pattern;
    }

    /** 解析入口（错误报告位置） */
    public static Ast parse(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("模式不得为 null");
        }
        if (pattern.length() > RegexSafety.MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("模式超限：> " + RegexSafety.MAX_PATTERN_LENGTH);
        }
        RegexParser parser = new RegexParser(pattern);
        Ast ast = parser.parseAlt();
        if (parser.pos < parser.pattern.length()) {
            throw new IllegalArgumentException("位置 " + parser.pos + "：意外的 '"
                    + parser.pattern.charAt(parser.pos) + "'");
        }
        return ast;
    }

    /** 已用捕获组数 */
    public static int groups(String pattern) {
        RegexParser parser = new RegexParser(pattern);
        parser.parseAlt();
        return parser.groupCount;
    }

    private Ast parseAlt() {
        List<Ast> branches = new ArrayList<>();
        branches.add(parseCat());
        while (pos < pattern.length() && pattern.charAt(pos) == '|') {
            pos++;
            branches.add(parseCat());
        }
        return branches.size() == 1 ? branches.get(0) : new Alt(branches);
    }

    private Ast parseCat() {
        List<Ast> parts = new ArrayList<>();
        while (pos < pattern.length()) {
            char c = pattern.charAt(pos);
            if (c == '|' || c == ')') {
                break;
            }
            Ast atom = parseAtom();
            parts.add(parseQuantifier(atom));
        }
        if (parts.isEmpty()) {
            return new Empty();
        }
        return parts.size() == 1 ? parts.get(0) : new Cat(parts);
    }

    private Ast parseQuantifier(Ast atom) {
        if (pos >= pattern.length()) {
            return atom;
        }
        char c = pattern.charAt(pos);
        int min;
        int max;
        if (c == '*') {
            min = 0;
            max = -1;
            pos++;
        } else if (c == '+') {
            min = 1;
            max = -1;
            pos++;
        } else if (c == '?') {
            min = 0;
            max = 1;
            pos++;
        } else if (c == '{') {
            int save = pos;
            int[] bounds = tryParseBounds();
            if (bounds == null) {
                pos = save;
                return atom;
            }
            min = bounds[0];
            max = bounds[1];
        } else {
            return atom;
        }
        boolean lazy = false;
        if (pos < pattern.length() && pattern.charAt(pos) == '?') {
            lazy = true;
            pos++;
        }
        if (max != -1 && max < min) {
            throw new IllegalArgumentException("位置 " + pos + "：{m,n} 上界小于下界");
        }
        return new Rep(atom, min, max, lazy);
    }

    private int[] tryParseBounds() {
        int save = pos;
        pos++;
        int start = pos;
        while (pos < pattern.length() && Character.isDigit(pattern.charAt(pos))) {
            pos++;
        }
        if (pos == start) {
            pos = save;
            return null;
        }
        int min = Integer.parseInt(pattern.substring(start, pos));
        int max = min;
        if (pos < pattern.length() && pattern.charAt(pos) == ',') {
            pos++;
            start = pos;
            while (pos < pattern.length() && Character.isDigit(pattern.charAt(pos))) {
                pos++;
            }
            max = pos == start ? -1 : Integer.parseInt(pattern.substring(start, pos));
        }
        if (pos >= pattern.length() || pattern.charAt(pos) != '}') {
            pos = save;
            return null;
        }
        pos++;
        if (min > RegexSafety.MAX_REPEAT || (max != -1 && max > RegexSafety.MAX_REPEAT)) {
            throw new IllegalArgumentException("位置 " + pos + "：重复上界超限（展开爆炸拒绝）");
        }
        return new int[]{min, max};
    }

    private Ast parseAtom() {
        char c = pattern.charAt(pos);
        if (c == '(') {
            pos++;
            if (pattern.startsWith("?:", pos)) {
                pos += 2;
                Ast plain = parseAlt();
                if (pos >= pattern.length() || pattern.charAt(pos) != ')') {
                    throw new IllegalArgumentException("位置 " + pos + "：分组未闭合");
                }
                pos++;
                return new Group(0, null, plain);
            }
            String name = null;
            if (pattern.startsWith("?P<", pos)) {
                int close = pattern.indexOf('>', pos + 3);
                if (close < 0) {
                    throw new IllegalArgumentException("位置 " + pos + "：命名组未闭合");
                }
                name = pattern.substring(pos + 3, close);
                if (name.isBlank()) {
                    throw new IllegalArgumentException("位置 " + pos + "：组名不得为空");
                }
                pos = close + 1;
            } else if (pos < pattern.length() && pattern.charAt(pos) == '?') {
                throw new IllegalArgumentException("位置 " + pos + "：不支持的组标志");
            }
            groupCount++;
            Ast body = parseAlt();
            if (pos >= pattern.length() || pattern.charAt(pos) != ')') {
                throw new IllegalArgumentException("位置 " + pos + "：分组未闭合");
            }
            pos++;
            return new Group(groupCount, name, body);
        }
        if (c == '[') {
            return parseClass();
        }
        if (c == '^') {
            pos++;
            return new Anchor(true);
        }
        if (c == '$') {
            pos++;
            return new Anchor(false);
        }
        if (c == '.') {
            pos++;
            return new ClassNode(List.of(
                    new CharClasses.Range(0, '\n' - 1),
                    new CharClasses.Range('\n' + 1, CharClasses.MAX_CP)), false);
        }
        if (c == '\\') {
            pos++;
            return parseEscape(false);
        }
        if (c == '*' || c == '+' || c == '?') {
            throw new IllegalArgumentException("位置 " + pos + "：量词缺少前置元素");
        }
        int cp = codePointAt();
        return new CharNode(cp);
    }

    private Ast parseEscape(boolean inClass) {
        if (pos >= pattern.length()) {
            throw new IllegalArgumentException("位置 " + pos + "：转义悬挂");
        }
        char c = pattern.charAt(pos);
        pos++;
        switch (c) {
            case 'd' -> {
                return classOf(CharClasses.digitRanges(), false);
            }
            case 'D' -> {
                return classOf(CharClasses.digitRanges(), true);
            }
            case 'w' -> {
                return classOf(CharClasses.wordRanges(), false);
            }
            case 'W' -> {
                return classOf(CharClasses.wordRanges(), true);
            }
            case 's' -> {
                return classOf(CharClasses.spaceRanges(), false);
            }
            case 'S' -> {
                return classOf(CharClasses.spaceRanges(), true);
            }
            case 'n' -> {
                return new CharNode('\n');
            }
            case 't' -> {
                return new CharNode('\t');
            }
            case 'r' -> {
                return new CharNode('\r');
            }
            case 'f' -> {
                return new CharNode('\f');
            }
            case 'v' -> {
                return new CharNode('\u000B');
            }
            default -> {
                if (Character.isLetterOrDigit(c) && !inClass) {
                    throw new IllegalArgumentException("位置 " + (pos - 1) + "：不支持的转义 \\" + c);
                }
                return new CharNode(c);
            }
        }
    }

    private Ast parseClass() {
        int openAt = pos;
        pos++;
        boolean negated = false;
        if (pos < pattern.length() && pattern.charAt(pos) == '^') {
            negated = true;
            pos++;
        }
        List<CharClasses.Range> ranges = new ArrayList<>();
        boolean first = true;
        while (pos < pattern.length() && (pattern.charAt(pos) != ']' || first)) {
            first = false;
            int lo;
            if (pattern.charAt(pos) == '\\') {
                pos++;
                Ast escaped = parseEscape(true);
                if (escaped instanceof ClassNode cls) {
                    ranges.addAll(cls.ranges());
                    continue;
                }
                lo = ((CharNode) escaped).codePoint();
            } else {
                lo = codePointAt();
            }
            if (pattern.startsWith("-", pos) && pos + 1 < pattern.length() && pattern.charAt(pos + 1) != ']') {
                pos++;
                int hi;
                if (pattern.charAt(pos) == '\\') {
                    pos++;
                    Ast escaped = parseEscape(true);
                    if (escaped instanceof ClassNode) {
                        throw new IllegalArgumentException("位置 " + pos + "：区间端点不可为类");
                    }
                    hi = ((CharNode) escaped).codePoint();
                } else {
                    hi = codePointAt();
                }
                if (hi < lo) {
                    throw new IllegalArgumentException("位置 " + openAt + "：区间逆序");
                }
                ranges.add(new CharClasses.Range(lo, hi));
            } else {
                ranges.add(new CharClasses.Range(lo, lo));
            }
        }
        if (pos >= pattern.length()) {
            throw new IllegalArgumentException("位置 " + openAt + "：字符类未闭合");
        }
        pos++;
        return new ClassNode(ranges, negated);
    }

    private static ClassNode classOf(List<CharClasses.Range> ranges, boolean negated) {
        return new ClassNode(ranges, negated);
    }

    private int codePointAt() {
        int cp = pattern.codePointAt(pos);
        pos += Character.charCount(cp);
        return cp;
    }

    /** 组数（parse 后有效） */
    int parsedGroups() {
        return groupCount;
    }
}
