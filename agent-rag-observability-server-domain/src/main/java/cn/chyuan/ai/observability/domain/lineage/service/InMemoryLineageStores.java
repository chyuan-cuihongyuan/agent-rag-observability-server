package cn.chyuan.ai.observability.domain.lineage.service;

import cn.chyuan.ai.observability.domain.lineage.service.AssetRegistry.AssetEntity;
import cn.chyuan.ai.observability.domain.lineage.service.LineageEventService.LineageRun;
import cn.chyuan.ai.observability.domain.lineage.service.LineageGraphOps.LineageEdge;
import cn.chyuan.ai.observability.domain.lineage.service.SchemaHistoryService.SchemaChange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 血缘域内存缺省实现（仓储缺省/单机演示/测试口径，与 InMemoryResilienceStore 同位）。
 *
 * @author chyuan
 */
public final class InMemoryLineageStores {

    private InMemoryLineageStores() {
    }

    /** 内存资产存储 */
    public static class InMemoryAssetStore implements AssetRegistry.AssetStore {
        private final Map<String, AssetEntity> rows = new ConcurrentHashMap<>();

        @Override
        public void insert(AssetEntity entity) {
            rows.put(entity.urn(), entity);
        }

        @Override
        public AssetEntity findByUrn(String urn) {
            return rows.get(urn);
        }

        @Override
        public List<AssetEntity> listAll() {
            return new ArrayList<>(rows.values());
        }
    }

    /** 内存血缘运行与边存储 */
    public static class InMemoryLineageRunStore implements LineageEventService.LineageRunStore {
        private final Map<String, LineageRun> runs = new ConcurrentHashMap<>();
        private final Map<String, LineageEdge> edges = new ConcurrentHashMap<>();
        private long edgeSequence = 0;

        @Override
        public boolean insertRunIfAbsent(LineageRun run) {
            return runs.putIfAbsent(run.eventKey(), run) == null;
        }

        @Override
        public int addEdgeIfAbsent(String fromUrn, String toUrn, String source) {
            String key = fromUrn + "->" + toUrn + "|" + source;
            if (edges.containsKey(key)) {
                return 0;
            }
            edges.putIfAbsent(key, new LineageEdge(++edgeSequence, fromUrn, toUrn, source));
            return 1;
        }

        @Override
        public List<LineageEdge> listEdges() {
            return new ArrayList<>(edges.values());
        }
    }

    /** 内存 schema 变更存储 */
    public static class InMemorySchemaChangeStore implements SchemaHistoryService.SchemaChangeStore {
        private final List<SchemaChange> rows = new ArrayList<>();
        private long sequence = 0;

        @Override
        public synchronized void insert(SchemaChange change) {
            SchemaChange withId = new SchemaChange(++sequence, change.assetUrn(), change.atMs(),
                    change.field(), change.change(), change.before(), change.after(), change.breaking());
            rows.add(withId);
        }

        @Override
        public synchronized List<SchemaChange> listByAsset(String assetUrn) {
            return rows.stream().filter(change -> change.assetUrn().equals(assetUrn)).toList();
        }

        @Override
        public synchronized List<SchemaChange> listAll() {
            return new ArrayList<>(rows);
        }
    }
}
