package cn.chyuan.ai.observability.domain.lineage.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * schema 变更检测与时间线（工单 0289 AK5，借鉴 Dagster 物化历史）—
 * 前后两版字段 schema diff：ADDED/REMOVED/TYPE_CHANGED/NULLABLE_CHANGED（REMOVED/TYPE_CHANGED=破坏性）；
 * 变更留档 asset_schema_change（观测库第 30 表），按资产分组倒序时间线。
 */
public class SchemaHistoryService {

    public static final String ADDED = "ADDED";
    public static final String REMOVED = "REMOVED";
    public static final String TYPE_CHANGED = "TYPE_CHANGED";
    public static final String NULLABLE_CHANGED = "NULLABLE_CHANGED";

    /** 单条 schema 变更记录 */
    public record SchemaChange(long id, String assetUrn, long atMs, String field, String change,
            String before, String after, boolean breaking) {
    }

    /** 字段 schema（type + nullable） */
    public record FieldSchema(String type, boolean nullable) {
    }

    /** schema 存储端口（asset_schema_change 第 30 表） */
    public interface SchemaChangeStore {

        void insert(SchemaChange change);

        List<SchemaChange> listByAsset(String assetUrn);

        List<SchemaChange> listAll();
    }

    private final SchemaChangeStore store;

    public SchemaHistoryService(SchemaChangeStore store) {
        this.store = store;
    }

    /**
     * 字段级 diff（纯函数）：变更清单（REMOVED/TYPE_CHANGED 标记破坏性）。
     */
    public static List<SchemaChange> detect(String assetUrn, long atMs,
            Map<String, FieldSchema> before, Map<String, FieldSchema> after) {
        List<SchemaChange> changes = new ArrayList<>();
        for (Map.Entry<String, FieldSchema> entry : after.entrySet()) {
            FieldSchema previous = before.get(entry.getKey());
            if (previous == null) {
                changes.add(new SchemaChange(0, assetUrn, atMs, entry.getKey(), ADDED,
                        null, entry.getValue().type(), false));
                continue;
            }
            if (!previous.type().equals(entry.getValue().type())) {
                changes.add(new SchemaChange(0, assetUrn, atMs, entry.getKey(), TYPE_CHANGED,
                        previous.type(), entry.getValue().type(), true));
            } else if (previous.nullable() != entry.getValue().nullable()) {
                changes.add(new SchemaChange(0, assetUrn, atMs, entry.getKey(), NULLABLE_CHANGED,
                        String.valueOf(previous.nullable()), String.valueOf(entry.getValue().nullable()), false));
            }
        }
        for (Map.Entry<String, FieldSchema> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                changes.add(new SchemaChange(0, assetUrn, atMs, entry.getKey(), REMOVED,
                        entry.getValue().type(), null, true));
            }
        }
        return changes;
    }

    /** 上报新版本：diff → 留档 → 返回本次变更（破坏性变更仍在留档中标记，不阻断） */
    public List<SchemaChange> recordVersion(String assetUrn, long atMs,
            Map<String, FieldSchema> before, Map<String, FieldSchema> after) {
        List<SchemaChange> changes = detect(assetUrn, atMs, before, after);
        for (SchemaChange change : changes) {
            store.insert(change);
        }
        return changes;
    }

    /** 时间线：按资产分组、时间倒序 */
    public Map<String, List<SchemaChange>> timeline(String assetUrn) {
        Map<String, List<SchemaChange>> out = new LinkedHashMap<>();
        List<SchemaChange> source = assetUrn == null || assetUrn.isBlank()
                ? store.listAll()
                : store.listByAsset(assetUrn);
        source.stream()
                .sorted((a, b) -> Long.compare(b.atMs(), a.atMs()))
                .forEach(change -> out.computeIfAbsent(change.assetUrn(), key -> new ArrayList<>()).add(change));
        return out;
    }
}
