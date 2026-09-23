package cn.chyuan.ai.observability.domain.regexkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 正则内核域单测（工单 0634-0640 BX1-BX7，ripgrep/RE2 思想）。
 * 解析与 Thompson NFA/Pike VM 最左优先/量词语义与 java.util.regex 对照/
 * 惰性 DFA 一致/捕获组与替换/字符类与 UTF-8/安全上限。
 */
class RegexKernelTest {

    private static RegexPort.InMemoryRegex port() {
        return new RegexPort.InMemoryRegex();
    }

    @Test
    void parserRejectsBrokenPatternsAtPosition() {
        assertThrows(IllegalArgumentException.class, () -> RegexParser.parse("a(b"));
        assertThrows(IllegalArgumentException.class, () -> RegexParser.parse("a[b"));
        assertThrows(IllegalArgumentException.class, () -> RegexParser.parse("*a"));
        assertThrows(IllegalArgumentException.class, () -> RegexParser.parse("a{3,2}"));
        assertThrows(IllegalArgumentException.class, () -> RegexParser.parse("(?P<>x)"));
        assertThrows(IllegalArgumentException.class, () -> RegexParser.parse("(?i)x"));
        assertThrows(IllegalArgumentException.class, () -> RegexParser.parse("\\q"));
        assertThrows(IllegalArgumentException.class, () -> RegexParser.parse("a{0,2000}"));
        assertThrows(IllegalArgumentException.class, () -> RegexParser.parse(null));
    }

    @Test
    void pikeVmMatchesBasicsWithCaptures() {
        RegexPort.Compiled compiled = port().compile("(?P<key>[a-z]+)=(?P<value>[0-9]+)");
        PikeVm.Match match = compiled.find("cfg timeout=30 max=100");
        assertTrue(match.present());
        assertEquals("timeout=30", match.group(0, "cfg timeout=30 max=100"));
        assertEquals("timeout", match.group(1, "cfg timeout=30 max=100"));
        assertEquals("30", match.group(2, "cfg timeout=30 max=100"));
        assertEquals(1, compiled.groups().groupOf("key"));
        assertEquals(2, compiled.groups().groupOf("value"));
        assertNull(match.group(9, "cfg timeout=30 max=100"), "未参与组语义");
        assertEquals("timeout", compiled.groups().expand("${key}",
                "cfg timeout=30 max=100", match));
    }

    @Test
    void quantifiersAgreeWithJavaRegex() {
        String[][] cases = {
                {"a+", "caaandy aa!"},
                {"a*?", "aaa"},
                {"<.+>", "<a><b><c>"},
                {"<.+?>", "<a><b><c>"},
                {"a{2,3}", "aaaa"},
                {"(ab|a)(b?)", "abb"},
                {"^(x|xy)+z$", "xyxz"},
                {"[a-c]+", "abcdef"},
                {"[^a-c]+", "abcdef"},
                {"\\d+-\\d+", "呼叫 138-0013 与 400-800"},
                {"中+文", "中中文字"},
                {"a??b", "ab"},
        };
        for (String[] c : cases) {
            RegexPort.Compiled compiled = port().compile(c[0]);
            java.util.regex.Matcher javaMatcher = Pattern.compile(c[0]).matcher(c[1]);
            if (javaMatcher.find()) {
                PikeVm.Match mine = compiled.find(c[1]);
                assertTrue(mine.present(), c[0] + " 应命中 " + c[1]);
                assertEquals(javaMatcher.start(), mine.start(), c[0] + " 起点应与 java 一致");
                assertEquals(javaMatcher.end(), mine.end(), c[0] + " 终点应与 java 一致（贪婪/懒惰边界）");
                if (javaMatcher.groupCount() >= 1 && javaMatcher.group(1) != null) {
                    assertEquals(javaMatcher.group(1), mine.group(1, c[1]), c[0] + " 组 1 一致");
                }
            } else {
                assertFalse(compiled.find(c[1]).present(), c[0] + " 不应命中 " + c[1]);
            }
        }
    }

    @Test
    void alternationPriorityIsLeftmostFirst() {
        RegexPort.Compiled compiled = port().compile("cat|catalog");
        PikeVm.Match match = compiled.find("the catalog");
        assertEquals("cat", match.group(0, "the catalog"), "首选分支优先（与回溯一致）");
        java.util.regex.Matcher javaMatcher = Pattern.compile("cat|catalog").matcher("the catalog");
        assertTrue(javaMatcher.find());
        assertEquals(javaMatcher.group(), match.group(0, "the catalog"));
    }

    @Test
    void findAllIteratesNonOverlapping() {
        RegexPort.Compiled compiled = port().compile("\\d+");
        List<PikeVm.Match> all = compiled.findAll("版本 12 与 345 于 6789 结束");
        assertEquals(3, all.size());
        assertEquals("12", all.get(0).group(0, "版本 12 与 345 于 6789 结束"));
        assertEquals("345", all.get(1).group(0, "版本 12 与 345 于 6789 结束"));
        assertEquals("6789", all.get(2).group(0, "版本 12 与 345 于 6789 结束"));
    }

    @Test
    void lazyDfaAgreesWithPikeVm() {
        String[] patterns = {"a+", "[a-z]+\\s", "(x|y)+z", "ab*c", ".*", "(?:ab)+", "^start", "end$"};
        String[] inputs = {"aaa", "abc def", "xyxyxz", "abbbc", "anything", "ababab", "start here", "the end"};
        for (String p : patterns) {
            RegexPort.Compiled compiled = port().compile(p);
            for (String input : inputs) {
                boolean vmSays = compiled.find(input).present();
                boolean dfaSays = compiled.contains(input);
                assertEquals(vmSays, dfaSays, p + " 双引擎应一致 @ " + input);
            }
        }
        assertTrue(port().compile("a+").contains("bbb aaa ccc"));
        assertFalse(port().compile("^x").contains("yx"));
    }

    @Test
    void capturesReplaceWithNamedTemplates() {
        RegexPort.Compiled compiled = port().compile("(?P<user>[a-z]+)@(?P<host>[a-z.]+)");
        String input = "chyuan@example.com and ops@obs.local";
        assertEquals("chyuan@example.com and ops@obs.local",
                compiled.replaceAll(input, "$0"));
        assertEquals("[user=chyuan] and [user=ops]",
                compiled.replaceAll(input, "[user=${user}]"));
        assertEquals("$0 and $0",
                compiled.replaceAll(input, "$$0"), "$$ 转义为字面 $");
        assertThrows(IllegalArgumentException.class,
                () -> compiled.replaceAll(input, "${missing}"));
        assertThrows(IllegalArgumentException.class,
                () -> compiled.replaceAll(input, "${"));
    }

    @Test
    void charClassesUnicodeAndPredefined() {
        RegexPort.Compiled chinese = port().compile("[\u4e00-\u9fff]+");
        PikeVm.Match match = chinese.find("abc 中文分词 kernel");
        assertEquals("中文分词", match.group(0, "abc 中文分词 kernel"), "多字节按码点正确匹配");
        java.util.regex.Matcher javaCn = Pattern.compile("[\u4e00-\u9fff]+").matcher("abc 中文分词 kernel");
        assertTrue(javaCn.find());
        assertEquals(javaCn.group(), match.group(0, "abc 中文分词 kernel"));

        assertTrue(port().compile("\\d+").contains("第 42 号"));
        assertFalse(port().compile("^\\d+$").matches("第 42 号"));
        assertTrue(port().compile("^\\d+$").matches("42"));
        assertTrue(port().compile("\\w+").contains("var_name"));
        assertTrue(port().compile("\\s+").contains("a\tb"));
        assertFalse(port().compile("a.b").contains("a\nb"), "点号默认除换行");
        assertTrue(port().compile("\\n").contains("a\nb"));
    }

    @Test
    void fullMatchAndEmptyMatchSemantics() {
        assertTrue(port().compile("a*b").matches("aaab"));
        assertFalse(port().compile("a*b").matches("aaabc"));
        assertTrue(port().compile("^$").matches(""));

        RegexPort.Compiled empty = port().compile("a*");
        assertEquals("XbX", empty.replaceAll("baa", "X"), "空匹配跳一字符语义");
        List<PikeVm.Match> empties = empty.findAll("b");
        assertEquals(2, empties.size(), "空串可命中两次（起点与终点）");
    }

    @Test
    void safetyCapsRejectHostilePatterns() {
        StringBuilder blowup = new StringBuilder();
        blowup.append("(a".repeat(60));
        blowup.append(")".repeat(60));
        assertThrows(IllegalArgumentException.class,
                () -> port().compile(blowup + "{1000}"),
                "重复展开规模超限拒绝（NFA 指令上限）");
        assertThrows(IllegalArgumentException.class, () -> RegexParser.parse("x".repeat(5000)));
        RegexPort.Compiled bounded = port().compile("(?:ab){500}");
        assertTrue(bounded.contains("ab".repeat(600)), "有界重复合法可用");
        assertFalse(bounded.matches("ab".repeat(499)));
    }
}
