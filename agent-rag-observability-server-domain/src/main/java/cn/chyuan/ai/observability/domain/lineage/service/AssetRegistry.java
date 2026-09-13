package cn.chyuan.ai.observability.domain.lineage.service;

import cn.chyuan.ai.observability.domain.lineage.service.AssetUrn;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资产注册（工单 0285 AK1，借鉴 DataHub entity）—
 * dataset/job/model 资产统一 URN 注册与检索；同 URN 重复注册拒绝。
 * 存储经 {@link AssetStore} 端口（infrastructure 落 asset_entity 观测库第 27 表）。
 */
public class AssetRegistry {

    /** 资产实体 */
    public record AssetEntity(String urn, String type, String qualifier, String displayName,
            String owner, String note) {

        public AssetEntity {
            AssetUrn parsed = AssetUrn.parse(urn);
            type = parsed.type();
            qualifier = parsed.qualifier();
        }
    }

    /** 资产存储端口（infrastructure 经 MyBatis 落 asset_entity 表） */
    public interface AssetStore {

        void insert(AssetEntity entity);

        AssetEntity findByUrn(String urn);

        List<AssetEntity> listAll();
    }

    private final AssetStore store;

    public AssetRegistry(AssetStore store) {
        this.store = store;
    }

    /** 注册（URN 重复拒绝） */
    public AssetEntity register(AssetEntity entity) {
        if (store.findByUrn(entity.urn()) != null) {
            throw new IllegalArgumentException("资产已注册: " + entity.urn());
        }
        store.insert(entity);
        return entity;
    }

    public AssetEntity get(String urn) {
        AssetEntity entity = store.findByUrn(urn);
        if (entity == null) {
            throw new IllegalArgumentException("资产不存在: " + urn);
        }
        return entity;
    }

    public boolean exists(String urn) {
        return store.findByUrn(urn) != null;
    }

    /** 检索（按类型过滤，名称排序） */
    public List<AssetEntity> list(String type) {
        return store.listAll().stream()
                .filter(entity -> type == null || type.isBlank() || entity.type().equals(type))
                .sorted(Comparator.comparing(AssetEntity::urn))
                .toList();
    }
}
