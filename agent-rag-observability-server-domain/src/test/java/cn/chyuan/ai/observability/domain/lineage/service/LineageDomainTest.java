package cn.chyuan.ai.observability.domain.lineage.service;

import cn.chyuan.ai.observability.domain.lineage.service.AssetRegistry.AssetEntity;
import cn.chyuan.ai.observability.domain.lineage.service.LineageEventService.LineageRunEvent;
import cn.chyuan.ai.observability.domain.lineage.service.LineageGraphOps.LineageEdge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 血缘域单测（工单 0285-0290 AK1-AK6）：URN/注册/事件补边幂等/遍历/影响分析/schema diff/健康分。
 */
class LineageDomainTest {

    private InMemoryLineageStores.InMemoryAssetStore assetStore = new InMemoryLineageStores.InMemoryAssetStore();
    private InMemoryLineageStores.InMemoryLineageRunStore runStore = new InMemoryLineageStores.InMemoryLineageRunStore();
    private InMemoryLineageStores.InMemorySchemaChangeStore schemaStore = new InMemoryLineageStores.InMemorySchemaChangeStore();

    private AssetRegistry registry() {
        return new AssetRegistry(assetStore);
    }

    private LineageEventService eventService() {
        return new LineageEventService(runStore);
    }

    @Test
    void URN组装解析与非法拒绝() {
        AssetUrn urn = AssetUrn.parse("urn:agent:dataset:warehouse.orders");
        assertEquals("dataset", urn.type());
        assertEquals("warehouse.orders", urn.qualifier());
        assertEquals("urn:agent:dataset:warehouse.orders", urn.urn());
        // 非法：类型白名单外 / 前缀错 / 空限定符
        assertThrows(IllegalArgumentException.class, () -> new AssetUrn("table", "x"));
        assertThrows(IllegalArgumentException.class, () -> AssetUrn.parse("urn:other:dataset:x"));
        assertThrows(IllegalArgumentException.class, () -> AssetUrn.parse("urn:agent:dataset:"));
        assertThrows(IllegalArgumentException.class, () -> new AssetUrn("dataset", "非 法"));
    }

    @Test
    void 资产注册去重与检索() {
        AssetRegistry registry = registry();
        registry.register(new AssetEntity("urn:agent:dataset:ods.orders", "dataset", "ods.orders", "订单原始表", "chen", null));
        // 重复注册拒绝
        assertThrows(IllegalArgumentException.class, () -> registry.register(
                new AssetEntity("urn:agent:dataset:ods.orders", "dataset", "ods.orders", null, null, null)));
        registry.register(new AssetEntity("urn:agent:job:sync_orders", "job", "sync_orders", null, null, null));
        assertEquals(2, registry.list(null).size());
        assertEquals(1, registry.list("job").size());
        assertTrue(registry.exists("urn:agent:dataset:ods.orders"));
        // 非法 URN 构造拒绝
        assertThrows(IllegalArgumentException.class, () -> new AssetEntity("bad", "dataset", "x", null, null, null));
    }

    @Test
    void 血缘事件三分支与幂等补边() {
        LineageEventService service = eventService();
        Set<String> inputs = Set.of("urn:agent:dataset:ods.orders");
        Set<String> outputs = Set.of("urn:agent:dataset:dws.orders_daily");
        assertTrue(service.ingest(new LineageRunEvent("run-1", LineageEventService.EVENT_START,
                "urn:agent:job:sync", inputs, outputs, 1_000, null)));
        assertTrue(service.ingest(new LineageRunEvent("run-2", LineageEventService.EVENT_COMPLETE,
                "urn:agent:job:sync", inputs, outputs, 2_000, null)));
        // COMPLETE 自动补边（input→output）
        assertEquals(1, service.currentEdges().size());
        assertEquals("auto", service.currentEdges().get(0).source());
        // 重复事件幂等（不再补边）
        assertFalse(service.ingest(new LineageRunEvent("run-2", LineageEventService.EVENT_COMPLETE,
                "urn:agent:job:sync", inputs, outputs, 2_000, null)));
        assertEquals(1, service.currentEdges().size());
        // 失败事件留因
        assertTrue(service.ingest(new LineageRunEvent("run-3", LineageEventService.EVENT_FAIL,
                "urn:agent:job:sync", inputs, outputs, 3_000, "boom")));
        // 非法事件类型
        assertThrows(IllegalArgumentException.class, () -> service.ingest(new LineageRunEvent("run-4",
                "RUN_WEIRD", "job", null, null, 0, null)));
    }

    @Test
    void 多跳遍历与影响分析分级() {
        LineageEventService service = eventService();
        service.ingest(new LineageRunEvent("e1", LineageEventService.EVENT_COMPLETE, "job1",
                Set.of("ods"), Set.of("dwd"), 1, null));
        service.ingest(new LineageRunEvent("e2", LineageEventService.EVENT_COMPLETE, "job2",
                Set.of("dwd"), Set.of("dws"), 2, null));
        service.ingest(new LineageRunEvent("e3", LineageEventService.EVENT_COMPLETE, "job3",
                Set.of("dws"), Set.of("app"), 3, null));
        List<LineageEdge> edges = service.currentEdges();
        // 上游溯源
        assertEquals(Map.of("dwd", 1, "ods", 2), LineageGraphOps.upstreamOf(edges, "dws", 8));
        // 下游扩散（深度上限）
        var downstream2 = LineageGraphOps.downstreamOf(edges, "ods", 2);
        assertEquals(2, downstream2.size());
        var downstream1 = LineageGraphOps.downstreamOf(edges, "ods", 1);
        assertEquals(1, downstream1.size());
        // 影响分析：直接下游 HIGH，间接 LOW，按跳数排序
        List<LineageGraphOps.ImpactRow> rows = LineageGraphOps.impactRows(edges, "ods", 8);
        assertEquals(3, rows.size());
        assertEquals("HIGH", rows.get(0).severity());
        assertEquals("dwd", rows.get(0).urn());
        assertEquals("LOW", rows.get(2).severity());
        // 环防护：成环时不死循环（深度上限截断）
        List<LineageEdge> cyclic = List.of(new LineageEdge(1, "a", "b", "manual"), new LineageEdge(2, "b", "a", "manual"));
        assertEquals(1, LineageGraphOps.downstreamOf(cyclic, "a", 8).size());
    }

    @Test
    void schema变更四分类与破坏性标记与时间线() {
        SchemaHistoryService service = new SchemaHistoryService(schemaStore);
        Map<String, SchemaHistoryService.FieldSchema> v1 = Map.of(
                "id", new SchemaHistoryService.FieldSchema("bigint", false),
                "name", new SchemaHistoryService.FieldSchema("varchar", true),
                "old", new SchemaHistoryService.FieldSchema("int", true));
        Map<String, SchemaHistoryService.FieldSchema> v2 = Map.of(
                "id", new SchemaHistoryService.FieldSchema("bigint", false),
                "name", new SchemaHistoryService.FieldSchema("text", true),
                "amount", new SchemaHistoryService.FieldSchema("decimal", false));
        List<SchemaHistoryService.SchemaChange> changes =
                service.recordVersion("urn:agent:dataset:ods.orders", 5_000, v1, v2);
        // old REMOVED（破坏）+ name TYPE_CHANGED（破坏）+ amount ADDED
        assertEquals(3, changes.size());
        assertEquals(2, changes.stream().filter(SchemaHistoryService.SchemaChange::breaking).count());
        // 时间线按资产分组倒序
        service.recordVersion("urn:agent:dataset:ods.orders", 6_000, v2, v2);
        var timeline = service.timeline("urn:agent:dataset:ods.orders");
        assertEquals(1, timeline.size());
        List<SchemaHistoryService.SchemaChange> line = timeline.get("urn:agent:dataset:ods.orders");
        assertEquals(3, line.size());
        // NULLABLE_CHANGED 非破坏
        service.recordVersion("urn:agent:dataset:ods.orders", 7_000,
                Map.of("flag", new SchemaHistoryService.FieldSchema("int", true)),
                Map.of("flag", new SchemaHistoryService.FieldSchema("int", false)));
        assertTrue(schemaStore.listByAsset("urn:agent:dataset:ods.orders").stream()
                .anyMatch(change -> SchemaHistoryService.NULLABLE_CHANGED.equals(change.change())
                        && !change.breaking()));
    }

    @Test
    void 资产健康分与分级() {
        AssetHealthCalculator calculator = new AssetHealthCalculator(3_600_000, 0.5);
        // 新鲜：刚完成 + 质量满分 = A
        var fresh = calculator.compute(new AssetHealthCalculator.HealthInput("u1", 1_000, 1_100, 1.0, true));
        assertEquals("A", fresh.grade());
        // 陈旧（2 个半衰期）+ 质量差 = D
        var stale = calculator.compute(new AssetHealthCalculator.HealthInput("u2", 0, 7_200_000, 0.1, true));
        assertEquals("D", stale.grade());
        // 无质量数据退化只用新鲜度
        var noQuality = calculator.compute(new AssetHealthCalculator.HealthInput("u3", 1_000, 1_100, 0, false));
        assertEquals(0.0, noQuality.qualityScore());
        assertEquals(fresh.freshnessScore(), noQuality.totalScore(), 1e-6);
        // 分级边界
        assertEquals("A", AssetHealthCalculator.grade(0.75));
        assertEquals("C", AssetHealthCalculator.grade(0.3));
    }

    @Test
    void 目录导出确定性与HTML() {
        var registry = registry();
        registry.register(new AssetEntity("urn:agent:dataset:a", "dataset", "a", "表A", "own", null));
        registry.register(new AssetEntity("urn:agent:dataset:b", "dataset", "b", "表B", "own", null));
        var healthStore = new AssetHealthCalculator.AssetHealthStore();
        healthStore.record(new AssetHealthCalculator.AssetHealth("urn:agent:dataset:a", 0.9, 0.8, 0.85, "A", 1));
        var catalog = AssetCatalogExporter.export(registry.list(null),
                List.of(new LineageEdge(1, "urn:agent:dataset:a", "urn:agent:dataset:b", "auto")),
                healthStore.latestAll());
        // 确定性键序：重复导出逐字节一致
        var again = AssetCatalogExporter.export(registry.list(null),
                List.of(new LineageEdge(1, "urn:agent:dataset:a", "urn:agent:dataset:b", "auto")),
                healthStore.latestAll());
        assertEquals(catalog.json(), again.json());
        assertEquals(2, catalog.assets());
        assertEquals(1, catalog.edges());
        // HTML 含资产与健康分级
        assertTrue(catalog.html().contains("表A") && catalog.html().contains("A ("));
        assertTrue(catalog.json().contains("\"grade\":\"A\""));
    }
}
