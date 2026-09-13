package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import cn.chyuan.ai.observability.domain.lineage.service.AssetCatalogExporter;
import cn.chyuan.ai.observability.domain.lineage.service.AssetHealthCalculator;
import cn.chyuan.ai.observability.domain.lineage.service.AssetRegistry;
import cn.chyuan.ai.observability.domain.lineage.service.AssetRegistry.AssetEntity;
import cn.chyuan.ai.observability.domain.lineage.service.AssetUrn;
import cn.chyuan.ai.observability.domain.lineage.service.LineageEventService;
import cn.chyuan.ai.observability.domain.lineage.service.LineageEventService.LineageRunEvent;
import cn.chyuan.ai.observability.domain.lineage.service.LineageGraphOps;
import cn.chyuan.ai.observability.domain.lineage.service.LineageGraphOps.ImpactRow;
import cn.chyuan.ai.observability.domain.lineage.service.LineageGraphOps.LineageEdge;
import cn.chyuan.ai.observability.domain.lineage.service.SchemaHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 血缘域端点（六期 AK 簇 0285-0292）—
 * 资产注册/检索（AK1）、血缘事件接入（AK3）、影响分析（AK4）、schema 时间线（AK5）、
 * 资产健康（AK6）、血缘子图 JSON（AK7）、目录导出（AK8）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping("/api/v1/lineage")
public class LineageController {

    private final AssetRegistry assetRegistry;
    private final LineageEventService eventService;
    private final SchemaHistoryService schemaHistory;
    private final AssetHealthCalculator.AssetHealthStore healthStore;

    public LineageController(@Autowired(required = false) AssetRegistry assetRegistry,
            @Autowired(required = false) LineageEventService eventService,
            @Autowired(required = false) SchemaHistoryService schemaHistory,
            @Autowired(required = false) AssetHealthCalculator.AssetHealthStore healthStore) {
        // 仓储缺省时构造内存兜底（单机演示/测试口径，与 ResilienceController 一致）
        this.assetRegistry = assetRegistry != null ? assetRegistry
                : new AssetRegistry(new cn.chyuan.ai.observability.domain.lineage.service.InMemoryLineageStores.InMemoryAssetStore());
        this.eventService = eventService != null ? eventService
                : new LineageEventService(new cn.chyuan.ai.observability.domain.lineage.service.InMemoryLineageStores.InMemoryLineageRunStore());
        this.schemaHistory = schemaHistory != null ? schemaHistory
                : new SchemaHistoryService(new cn.chyuan.ai.observability.domain.lineage.service.InMemoryLineageStores.InMemorySchemaChangeStore());
        this.healthStore = healthStore != null ? healthStore : new AssetHealthCalculator.AssetHealthStore();
    }

    /** AK1：注册资产 */
    @PostMapping("/assets")
    public Response<Map<String, Object>> registerAsset(@RequestBody AssetEntity entity) {
        try {
            AssetEntity saved = assetRegistry.register(entity);
            Map<String, Object> out = new HashMap<>();
            out.put("urn", saved.urn());
            out.put("type", saved.type());
            out.put("displayName", saved.displayName());
            return Response.success(out);
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    /** AK1：资产清单（按类型过滤） */
    @GetMapping("/assets")
    public Response<List<AssetEntity>> listAssets(@RequestParam(required = false) String type) {
        return Response.success(assetRegistry.list(type));
    }

    /** AK3：血缘事件接入（eventKey 幂等；COMPLETE 自动补边） */
    @PostMapping("/events")
    public Response<Map<String, Object>> ingestEvent(@RequestBody LineageRunEvent event) {
        try {
            boolean first = eventService.ingest(event);
            Map<String, Object> out = new HashMap<>();
            out.put("processed", first);
            out.put("duplicate", !first);
            return Response.success(out);
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    /** AK7：血缘子图 JSON（以资产为中心 N 跳上下游） */
    @GetMapping("/graph")
    public Response<Map<String, Object>> graph(@RequestParam String urn,
            @RequestParam(defaultValue = "2") int depth) {
        int capped = Math.min(Math.max(depth, 1), 8);
        List<LineageEdge> edges = eventService.currentEdges();
        Map<String, Integer> upstream = LineageGraphOps.upstreamOf(edges, urn, capped);
        Map<String, Integer> downstream = LineageGraphOps.downstreamOf(edges, urn, capped);
        Map<String, Object> out = new HashMap<>();
        out.put("urn", urn);
        out.put("upstream", upstream);
        out.put("downstream", downstream);
        out.put("edges", edges);
        out.put("health", healthStore.latest(urn));
        return Response.success(out);
    }

    /** AK4：影响分析（下游波及面 + 留档语义由调用方组织） */
    @PostMapping("/impact")
    public Response<Map<String, Object>> impact(@RequestBody Map<String, String> body) {
        String urn = body.get("urn");
        if (urn == null || urn.isBlank()) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "urn 不能为空");
        }
        int depth = Integer.parseInt(body.getOrDefault("depth", "8"));
        List<ImpactRow> rows = LineageGraphOps.impactRows(eventService.currentEdges(), urn,
                Math.min(Math.max(depth, 1), 16));
        Map<String, Object> out = new HashMap<>();
        out.put("urn", urn);
        out.put("impacted", rows.size());
        out.put("highSeverity", rows.stream().filter(row -> "HIGH".equals(row.severity())).count());
        out.put("rows", rows);
        return Response.success(out);
    }

    /** AK5：schema 变更时间线（按资产分组倒序） */
    @GetMapping("/schema-timeline")
    public Response<Map<String, List<SchemaHistoryService.SchemaChange>>> schemaTimeline(
            @RequestParam(required = false) String urn) {
        return Response.success(schemaHistory.timeline(urn));
    }

    /** AK6：资产健康（联动质量断言通过率端口由装配注入；此处按已计算快照读取） */
    @GetMapping("/health")
    public Response<Map<String, AssetHealthCalculator.AssetHealth>> health() {
        return Response.success(healthStore.latestAll());
    }

    /** AK8：资产目录导出（catalog.json + 简版 HTML） */
    @GetMapping("/catalog/export")
    public Response<Map<String, Object>> exportCatalog() {
        List<AssetEntity> assets = assetRegistry.list(null);
        List<LineageEdge> edges = eventService.currentEdges();
        AssetCatalogExporter.Catalog catalog = AssetCatalogExporter.export(assets, edges, healthStore.latestAll());
        Map<String, Object> out = new HashMap<>();
        out.put("json", catalog.json());
        out.put("html", catalog.html());
        out.put("assets", catalog.assets());
        out.put("edges", catalog.edges());
        return Response.success(out);
    }

    /** URN 合法性预检（组装/解析纯函数暴露） */
    @PostMapping("/assets/validate-urn")
    public Response<Map<String, Object>> validateUrn(@RequestBody Map<String, String> body) {
        try {
            AssetUrn urn = AssetUrn.parse(body.get("urn"));
            return Response.success(Map.of("valid", true, "type", urn.type(), "qualifier", urn.qualifier()));
        } catch (IllegalArgumentException e) {
            Map<String, Object> out = new HashMap<>();
            out.put("valid", false);
            out.put("reason", e.getMessage());
            return Response.success(out);
        }
    }
}
