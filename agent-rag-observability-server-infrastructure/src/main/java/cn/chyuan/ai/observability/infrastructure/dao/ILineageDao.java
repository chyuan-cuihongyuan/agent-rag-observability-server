package cn.chyuan.ai.observability.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 血缘域 DAO（工单 0285-0290 AK 簇）：asset_entity/lineage_edge/lineage_run/asset_schema_change 四表合并。
 */
@Mapper
public interface ILineageDao {

    int insertAsset(@Param("urn") String urn, @Param("type") String type,
            @Param("qualifier") String qualifier, @Param("displayName") String displayName,
            @Param("owner") String owner, @Param("note") String note);

    Map<String, Object> selectAssetByUrn(@Param("urn") String urn);

    List<Map<String, Object>> selectAssets();

    int insertEdge(@Param("fromUrn") String fromUrn, @Param("toUrn") String toUrn,
            @Param("source") String source);

    Map<String, Object> selectEdge(@Param("fromUrn") String fromUrn, @Param("toUrn") String toUrn,
            @Param("source") String source);

    List<Map<String, Object>> selectEdges();

    int insertRun(@Param("eventKey") String eventKey, @Param("eventType") String eventType,
            @Param("jobUrn") String jobUrn, @Param("inputsJson") String inputsJson,
            @Param("outputsJson") String outputsJson, @Param("atMs") long atMs,
            @Param("error") String error);

    Map<String, Object> selectRunByEventKey(@Param("eventKey") String eventKey);

    int insertSchemaChange(@Param("assetUrn") String assetUrn, @Param("atMs") long atMs,
            @Param("field") String field, @Param("change") String change,
            @Param("before") String before, @Param("after") String after,
            @Param("breaking") boolean breaking);

    List<Map<String, Object>> selectSchemaChangesByAsset(@Param("assetUrn") String assetUrn);

    List<Map<String, Object>> selectSchemaChanges();
}
