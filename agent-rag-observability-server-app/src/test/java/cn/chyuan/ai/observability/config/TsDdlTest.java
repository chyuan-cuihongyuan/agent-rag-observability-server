package cn.chyuan.ai.observability.config;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时序样本表 DDL 双方言守卫（工单 0486 BF7，第 34 表）。
 */
class TsDdlTest {

    @Test
    void mysqlSchemaDefinesTsSample() throws IOException {
        String ddl = read("../sql/mysql-schema.sql");
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS ts_sample"), "MySQL 应含时序样本表");
        assertTrue(ddl.contains("UNIQUE KEY uk_ts_sample (batch_id, series_id, timestamp_ms)"), "MySQL 应含批次幂等唯一键");
        assertTrue(ddl.contains("工单 0486 BF7"), "MySQL DDL 应带工单口径注释");
    }

    @Test
    void postgresqlSchemaDefinesTsSample() throws IOException {
        String ddl = read("../sql/postgresql-schema.sql");
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS ts_sample"), "PG 应含时序样本表");
        assertTrue(ddl.contains("CONSTRAINT uk_ts_sample UNIQUE (batch_id, series_id, timestamp_ms)"), "PG 应含批次幂等唯一约束");
        assertTrue(ddl.contains("COMMENT ON TABLE ts_sample IS"), "PG 应含表注释");
    }

    private String read(String path) throws IOException {
        File file = new File(path);
        assertTrue(file.isFile(), "schema 文件应存在: " + path);
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
