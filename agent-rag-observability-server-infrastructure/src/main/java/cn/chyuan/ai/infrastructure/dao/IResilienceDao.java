package cn.chyuan.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 调度韧性聚合 DAO（工单 0220-0223 AD 簇；双方言公共子集 SQL，行以 map 承载）
 */
@Mapper
public interface IResilienceDao {

    int insertLag(@Param("topic") String topic, @Param("lag") long lag, @Param("level") String level);

    List<Map<String, Object>> selectRecentLags(@Param("limit") int limit);

    int insertQualityRule(@Param("name") String name, @Param("target") String target,
            @Param("field") String field, @Param("type") String type,
            @Param("paramsJson") String paramsJson, @Param("enabled") boolean enabled);

    int updateQualityRule(@Param("name") String name, @Param("target") String target,
            @Param("field") String field, @Param("type") String type,
            @Param("paramsJson") String paramsJson, @Param("enabled") boolean enabled);

    Map<String, Object> selectQualityRule(@Param("name") String name);

    List<Map<String, Object>> selectQualityRules();

    int deleteQualityRule(@Param("name") String name);

    int insertQualityResult(@Param("ruleName") String ruleName, @Param("pass") boolean pass,
            @Param("checked") int checked, @Param("violated") int violated,
            @Param("samplesJson") String samplesJson);

    List<Map<String, Object>> selectRecentQualityResults(@Param("limit") int limit);

    int insertBackfill(@Param("jobId") String jobId, @Param("name") String name,
            @Param("rangeStart") long rangeStart, @Param("rangeEnd") long rangeEnd,
            @Param("shardCount") int shardCount, @Param("completedJson") String completedJson,
            @Param("status") String status);

    int updateBackfill(@Param("jobId") String jobId, @Param("completedJson") String completedJson,
            @Param("status") String status);

    Map<String, Object> selectBackfill(@Param("jobId") String jobId);

    int insertSlaMiss(@Param("task") String task, @Param("expectedMs") long expectedMs,
            @Param("actualMs") long actualMs, @Param("overdueMs") long overdueMs);

    List<Map<String, Object>> selectRecentSlaMisses(@Param("limit") int limit);
}
