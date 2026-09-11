package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 日志保留清理 mapper（工单 0152 U6）：purgeBatch 按 databaseId 成对分叉——
 * MySQL 用 DELETE ... ORDER BY id LIMIT；PG 用 id IN (SELECT ... LIMIT) 子查询
 * （MySQL 不支持 IN 子查询带 LIMIT，两方言无公共写法）。表名走服务层白名单。
 */
@Mapper
public interface RetentionMapper {

    /** 删除一批超期日志，返回删除行数 */
    int purgeBatch(@Param("table") String table,
                   @Param("beforeTime") String beforeTime,
                   @Param("batchSize") int batchSize);

    /** 统计超期日志条数（dry-run 用） */
    long countPurge(@Param("table") String table,
                    @Param("beforeTime") String beforeTime);
}
