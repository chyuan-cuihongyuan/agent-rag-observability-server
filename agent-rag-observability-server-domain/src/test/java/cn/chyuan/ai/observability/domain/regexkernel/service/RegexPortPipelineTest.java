package cn.chyuan.ai.observability.domain.regexkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 正则端口组合管线测试（工单 0641 BX8）。
 * regex-kernel.enabled 默认关（开启才改变行为）；
 * 与 logkernel 只读联动：LogQL regexp 管道阶段可切换匹配器可选形态。
 */
class RegexPortPipelineTest {

    @Test
    void logqlRegexpStageShapeThroughPort() {
        RegexPort port = new RegexPort.InMemoryRegex();
        // LogQL 管道阶段形态：{job="api"} | regexp "(?P<method>\\w+) (?P<path>\\S+)"
        RegexPort.Compiled compiled = port.compile("(?P<method>\\w+) (?P<path>\\S+)");
        String line = "GET /api/v1/query";
        PikeVm.Match match = compiled.find(line);
        assertTrue(match.present());
        assertEquals("GET", match.group(1, line));
        assertEquals("/api/v1/query", match.group(2, line));

        assertTrue(port.lineMatches("(ERROR|WARN).*timeout", "2026-09-23 ERROR retry timeout=3s"));
        assertFalse(port.lineMatches("(ERROR|WARN).*timeout", "2026-09-23 INFO ok"));
        assertThrows(IllegalArgumentException.class, () -> port.lineMatches(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> port.lineMatches("x", null));
    }

    @Test
    void pipelineExtractsLogFieldsEndToEnd() {
        RegexPort port = new RegexPort.InMemoryRegex();
        RegexPort.Compiled levelPattern = port.compile(
                "\\[(?P<level>TRACE|DEBUG|INFO|WARN|ERROR)\\]\\s+(?P<msg>[^]]+)");
        String line = "ts=10:00 [ERROR] upstream call failed";
        PikeVm.Match match = levelPattern.find(line);
        assertEquals("ERROR", match.group(1, line));
        assertEquals("upstream call failed", match.group(2, line));
        String normalized = levelPattern.replaceAll(line, "L=${level} M=${msg}");
        assertEquals("ts=10:00 L=ERROR M=upstream call failed", normalized);
    }

    @Test
    void dualEnginesConsistentAcrossTraffic() {
        RegexPort port = new RegexPort.InMemoryRegex();
        RegexPort.Compiled compiled = port.compile(
                "(?P<ip>\\d+\\.\\d+\\.\\d+\\.\\d+) - (?P<user>\\w+) \"(?P<verb>GET|POST) (?P<url>[^\"]+)\"");
        List<String> lines = List.of(
                "10.0.0.1 - alice \"GET /a\"",
                "10.0.0.2 - bob \"POST /b/c\"",
                "noise line",
                "10.0.0.3 - carol \"GET /中文/路径\"");
        for (String line : lines) {
            assertEquals(compiled.find(line).present(), compiled.contains(line),
                    "双引擎一致 @ " + line);
        }
        assertEquals(3, compiled.findAll(String.join("\n", lines)).size());
        assertTrue(compiled.find("10.0.0.3 - carol \"GET /中文/路径\"").group(4,
                "10.0.0.3 - carol \"GET /中文/路径\"").contains("中文"));
    }

    @Test
    void compileRejectsHostileAndBrokenInputs() {
        RegexPort port = new RegexPort.InMemoryRegex();
        assertThrows(IllegalArgumentException.class, () -> port.compile("(unclosed"));
        assertThrows(IllegalArgumentException.class, () -> port.compile("a{5000}"));
        RegexPort.Compiled ok = port.compile("ok+");
        assertTrue(ok.contains("aokk"));
        assertTrue(ok.programSize() > 0);
        assertTrue(ok.dfaCachedStates() >= 0);
    }
}
