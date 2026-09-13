package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.lineage.service.SchemaHistoryService;
import cn.chyuan.ai.observability.domain.lineage.service.SchemaHistoryService.SchemaChange;
import cn.chyuan.ai.observability.infrastructure.dao.ILineageDao;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * schema 变更仓储实现（工单 0289 AK5）：实现 {@link SchemaHistoryService.SchemaChangeStore} 端口，
 * 经 MyBatis 落 asset_schema_change 表（双方言公共子集 SQL）。
 *
 * @author chyuan
 */
@Repository
public class LineageSchemaRepository implements SchemaHistoryService.SchemaChangeStore {

    @Resource
    private ILineageDao dao;

    @Override
    public void insert(SchemaChange change) {
        dao.insertSchemaChange(change.assetUrn(), change.atMs(), change.field(), change.change(),
                change.before(), change.after(), change.breaking());
    }

    @Override
    public List<SchemaChange> listByAsset(String assetUrn) {
        return dao.selectSchemaChangesByAsset(assetUrn).stream()
                .map(LineageSchemaRepository::toSchemaChange).toList();
    }

    @Override
    public List<SchemaChange> listAll() {
        return dao.selectSchemaChanges().stream()
                .map(LineageSchemaRepository::toSchemaChange).toList();
    }

    private static SchemaChange toSchemaChange(Map<String, Object> row) {
        Object breaking = row.get("breaking");
        boolean isBreaking = Boolean.TRUE.equals(breaking) || Integer.valueOf(1).equals(breaking);
        return new SchemaChange(((Number) row.get("id")).longValue(), (String) row.get("assetUrn"),
                ((Number) row.get("atMs")).longValue(), (String) row.get("field"),
                (String) row.get("change"), (String) row.get("before"), (String) row.get("after"),
                isBreaking);
    }
}
