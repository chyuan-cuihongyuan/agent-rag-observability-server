package cn.chyuan.ai.observability.domain.errorkernel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 错误聚合内核域单测（工单 0695-0700 CE1-CE6，sentry 思想）。
 * 事件归一清洗与拒绝/消息模板化与默认指纹/组统计 top 排序与状态/
 * 相似兜底阈值并组与组上限/flare 突增静默抑制/发布健康回归判定。
 */
class ErrorKernelTest {

    private static ErrorEvent.Event event(String type, String value, String fn, long at, String release) {
        return ErrorEvent.normalize(type, value,
                List.of(new ErrorEvent.Frame("app", "outer", 1), new ErrorEvent.Frame("app", fn, 10)),
                Map.of(), at, release, null);
    }

    @Test
    void normalizeCleansAndRejects() {
        ErrorEvent.Event event = ErrorEvent.normalize(
                " NPE ",
                "  x".repeat(300) + " ",
                java.util.Arrays.asList(new ErrorEvent.Frame("m", "f", 1), null,
                        new ErrorEvent.Frame("m", "f", 1), new ErrorEvent.Frame("m2", "f2", 2)),
                Map.of("env", "prod"),
                1000L, " r1 ", null);
        assertEquals("NPE", event.type());
        assertTrue(event.value().length() <= ErrorEvent.MAX_VALUE_LENGTH, "超长值截断");
        assertEquals(2, event.frames().size(), "null 帧剔除+相邻重复去重");
        assertEquals("prod", event.tags().get("env"));
        assertEquals("r1", event.release());
        assertNotNull(event.eventId());
        assertThrows(IllegalArgumentException.class,
                () -> ErrorEvent.normalize(null, "v", List.of(), Map.of(), 1L, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> ErrorEvent.normalize("T", " ", List.of(), Map.of(), 1L, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> ErrorEvent.normalize("T", "v", List.of(), Map.of(), 0L, null, null), "时间戳必须为正");
    }

    @Test
    void fingerprintTemplatingAndCustomFn() {
        ErrorEvent.Event a = event("DBError", "timeout for user 123 at /data/x.json", "query", 1000L, null);
        ErrorEvent.Event b = event("DBError", "timeout for user 456 at /data/y.json", "query", 2000L, null);
        FingerprintGrouper grouper = new FingerprintGrouper();
        assertEquals(grouper.fingerprint(a), grouper.fingerprint(b), "变参模板化后同指纹");
        String templated = FingerprintGrouper.template("uuid 550e8400-e29b-41d4-a716-446655440000 id 7 'quoted'");
        assertEquals("uuid U id N S", templated);
        assertEquals("query|DBError|timeout for user N at P", grouper.fingerprint(a), "默认指纹=顶层帧|类型|模板化值");
        FingerprintGrouper custom = new FingerprintGrouper(e -> e.type());
        assertEquals("DBError", custom.fingerprint(a));
        FingerprintGrouper bad = new FingerprintGrouper(e -> "");
        assertThrows(IllegalArgumentException.class, () -> bad.fingerprint(a));
        assertEquals(grouper.groupKey(a), grouper.groupKey(b), "同指纹同组键稳定");
        assertNotEquals(grouper.groupKey(a), grouper.groupKey(event("DBError", "timeout", "query", 1000L, null)),
                "不同内容不同组键");
    }

    @Test
    void groupStatsCountSeenStatusAndTop() {
        ErrorGroupStore store = new ErrorGroupStore();
        store.record("g1", "fp1", "title-1", 100);
        store.record("g1", "fp1", "title-1", 300);
        store.record("g2", "fp2", "title-2", 200);
        assertEquals(2, store.size());
        assertEquals(2, store.get("g1").count());
        assertEquals(100, store.get("g1").firstSeenMs());
        assertEquals(300, store.get("g1").lastSeenMs());
        assertEquals(ErrorGroupStore.UNRESOLVED, store.get("g1").status());

        store.record("g3", "fp3", "title-3", 150);
        store.record("g3", "fp3", "title-3", 250);
        List<ErrorGroupStore.Group> top = store.topProblems(2);
        assertEquals("g1", top.get(0).groupKey(), "计数最高居首");
        assertEquals("g3", top.get(1).groupKey(), "同计数按 lastSeen 新者优先");
        assertThrows(IllegalArgumentException.class, () -> store.topProblems(0));

        store.get("g3").resolve();
        assertEquals(ErrorGroupStore.RESOLVED, store.get("g3").status());
        store.get("g3").reopen();
        assertEquals(ErrorGroupStore.UNRESOLVED, store.get("g3").status());
    }

    @Test
    void similarityFallbackMergingAndGroupCap() {
        assertEquals(1.0, SimilarityGrouper.similarity("timeout db a", "timeout DB a"), 1e-9, "大小写无关全同");
        assertTrue(SimilarityGrouper.similarity("timeout connecting to db pool x", "timeout connecting to db pool y") > 0.7);
        assertTrue(SimilarityGrouper.similarity("timeout db", "disk full on /") < 0.3);

        SimilarityGrouper grouper = new SimilarityGrouper(0.6, 3);
        Map<String, String> titles = new java.util.LinkedHashMap<>();
        titles.put("g0", "timeout connecting to db pool");
        assertEquals("g0", grouper.assignNearest("timeout connecting to db pool slow", titles, 1),
                "相似度达阈值并入近邻组");
        assertNull(grouper.assignNearest("disk full on device", titles, 1), "未达阈值且未满员新建");
        titles.put("g1", "disk full on device");
        titles.put("g2", "other a");
        titles.put("g3", "other b");
        assertEquals("g0", grouper.assignNearest("totally different text here", titles, 4),
                "组数达上限强制并入最接近组");
        assertThrows(IllegalArgumentException.class, () -> new SimilarityGrouper(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new SimilarityGrouper(0.5, 0));
        assertThrows(IllegalArgumentException.class, () -> grouper.assignNearest(" ", titles, 1));
    }

    @Test
    void flareDetectionAndSilenceSuppression() {
        FlareDetector detector = new FlareDetector(60_000L, 3, 2.0d, 120_000L);
        String group = "g";
        // 基线：三个窗口各 2 条
        for (long i = 0; i < 2; i++) {
            detector.record(group, 60_000L + i);
            detector.record(group, 120_000L + i);
            detector.record(group, 180_000L + i);
        }
        // 突增：当前窗口 20 条
        for (long i = 0; i < 20; i++) {
            detector.record(group, 240_000L + i);
        }
        List<FlareDetector.Flare> flares = detector.detect(group, 240_001L);
        assertEquals(1, flares.size(), "突增窗口产出 flare");
        assertEquals(240_000L, flares.get(0).windowStartMs());
        assertEquals(20, flares.get(0).count());
        // 静默期内重复检测被抑制
        assertTrue(detector.detect(group, 250_000L).isEmpty(), "静默期抑制重复告警");
        // 静默期过后再次更猛突增可再告（上次突增已抬基线）
        for (long i = 0; i < 100; i++) {
            detector.record(group, 400_000L + i);
        }
        assertEquals(1, detector.detect(group, 400_001L).size(), "静默期后恢复告警");
        // 平稳组不告警
        assertTrue(detector.detect("steady", 240_000L).isEmpty(), "无事件组不告警");
        assertThrows(IllegalArgumentException.class, () -> new FlareDetector(0, 3, 2, 0));
    }

    @Test
    void releaseHealthRegression() {
        ReleaseHealth health = new ReleaseHealth();
        for (long i = 0; i < 10; i++) {
            health.record("v1", i);
        }
        for (long i = 0; i < 40; i++) {
            health.record("v2", 100 + i);
        }
        List<ReleaseHealth.ReleaseCount> counts = health.countsByRecent();
        assertEquals("v2", counts.get(0).release(), "最近版本在前");
        assertEquals(40, counts.get(0).count());
        assertTrue(health.regressions(2.0).get(0).release().equals("v2"), "v2 对 v1 计数比 4 倍判回归");
        assertEquals(4.0, health.regressions(2.0).get(0).ratio(), 1e-9);
        assertTrue(health.regressions(5.0).isEmpty(), "阈值 5 倍时不判回归");
        assertThrows(IllegalArgumentException.class, () -> health.regressions(0));
        assertThrows(IllegalArgumentException.class, () -> health.record(null, 1));
    }
}
