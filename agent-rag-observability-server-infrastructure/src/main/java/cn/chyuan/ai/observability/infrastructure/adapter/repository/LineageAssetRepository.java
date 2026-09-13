package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.lineage.service.AssetRegistry;
import cn.chyuan.ai.observability.domain.lineage.service.AssetRegistry.AssetEntity;
import cn.chyuan.ai.observability.infrastructure.dao.ILineageDao;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 资产仓储实现（工单 0285 AK1）：实现 {@link AssetRegistry.AssetStore} 端口，
 * 经 MyBatis 落 asset_entity 表（双方言公共子集 SQL）。
 *
 * @author chyuan
 */
@Repository
public class LineageAssetRepository implements AssetRegistry.AssetStore {

    @Resource
    private ILineageDao dao;

    @Override
    public void insert(AssetEntity entity) {
        dao.insertAsset(entity.urn(), entity.type(), entity.qualifier(), entity.displayName(),
                entity.owner(), entity.note());
    }

    @Override
    public AssetEntity findByUrn(String urn) {
        return toAsset(dao.selectAssetByUrn(urn));
    }

    @Override
    public List<AssetEntity> listAll() {
        return dao.selectAssets().stream().map(LineageAssetRepository::toAsset).toList();
    }

    private static AssetEntity toAsset(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new AssetEntity((String) row.get("urn"), (String) row.get("type"),
                (String) row.get("qualifier"), (String) row.get("displayName"),
                (String) row.get("owner"), (String) row.get("note"));
    }
}
