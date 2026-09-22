package cn.chyuan.ai.observability.config;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志流表 DDL 双方言守卫（工单 0579 BQ7，第 35 表）。
 */
class LogStreamDdlTest {

    @Test
    void mysqlSchemaDefinesLogStream() throws IOException {
        String ddl = read("../sql/mysql-schema.sql");
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS log_stream"), "MySQL 应含日志流表");
        assertTrue(ddl.contains("UNIQUE KEY uk_log_stream_fingerprint (fingerprint)"), "MySQL 应含流指纹唯一键");
        assertTrue(ddl.contains("工单 0579 BQ7"), "MySQL DDL 应带工单口径注释");
    }

    @Test
    void postgresqlSchemaDefinesLogStream() throws IOException {
        String ddl = read("../sql/postgresql-schema.sql");
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS log_stream"), "PG 应含日志流表");
        assertTrue(ddl.contains("CONSTRAINT uk_log_stream_fingerprint UNIQUE (fingerprint)"), "PG 应含流指纹唯一约束");
        assertTrue(ddl.contains("COMMENT ON TABLE log_stream IS"), "PG 应含表注释");
    }

    private String read(String path) throws IOException {
        File file = new File(path);
        assertTrue(file.isFile(), "schema 文件应存在: " + path);
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
