package cn.chyuan.ai.observability.domain.errorkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ErrorPort 组合管线测试（工单 0702 CE8，sentry 思想）。
 * ingest→group→aggregate→flare→health 全链/日志行作事件源形态
 * （logkernel 只读联动）/非法入参拒绝。
 */
class ErrorPortPipelineTest {

    @Test
    void portIngestGroupAggregateTop() {
        ErrorPort port = new ErrorPort.InMemoryErrorAggregator();
        List<ErrorEvent.Event> events = List.of(
                event("NPE", "x is null", "read", 1000, "v1"),
                event("NPE", "x is null", "read", 2000, "v1"),
                event("DBError", "timeout 1", "query", 1500, "v1"),
                event("DBError", "timeout 2", "query", 2500, "v1"),
                event("DBError", "timeout 3", "query", 3000, "v1"));
        ErrorPort.IngestResult result = port.ingest(events);
        assertEquals(5, result.ingested());
        assertEquals(2, result.groups(), "模板化归一：NPE 一组 DBError 一组");
        ErrorPort.Snapshot snapshot = port.snapshot(2, 2.0);
        assertEquals("DBError", snapshot.topProblems().get(0).fingerprint().split("\\|")[1], "计数 3 的组居首");
        assertEquals(3, snapshot.topProblems().get(0).count());
    }

    @Test
    void portIngestFromLogLinesLinkage() {
        ErrorPort port = new ErrorPort.InMemoryErrorAggregator();
        List<String> lines = List.of(
                "[ERROR] NPE: user is null (service.read:10)",
                "[ERROR] NPE: user is null (service.read:11)",
                "[ERROR] DBError: timeout (dao.query:20)");
        ErrorPort.IngestResult result = port.ingestLogLines(lines, 10_000L);
        assertEquals(3, result.ingested());
        assertEquals(2, result.groups(), "同模板日志行同组");
        ErrorPort.Snapshot snapshot = port.snapshot(1, 2.0);
        assertEquals(2, snapshot.topProblems().get(0).count(), "NPE 组计数 2");
    }

    @Test
    void portRejectsIllegal() {
        ErrorPort port = new ErrorPort.InMemoryErrorAggregator();
        assertThrows(IllegalArgumentException.class, () -> port.ingest(null));
        assertThrows(IllegalArgumentException.class, () -> port.eventsFromLogLines(null, 1));
    }

    private static ErrorEvent.Event event(String type, String value, String fn, long at, String release) {
        return ErrorEvent.normalize(type, value,
                List.of(new ErrorEvent.Frame("app", fn, 10)), Map.of(), at, release, null);
    }
}
