package cn.chyuan.ai.observability.config;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异常事件表 DDL 双方言守卫（工单 0412 AX8，第 33 表）。
 */
class AnomalyDdlTest {

    @Test
    void mysqlSchemaDefinesAnomalyEvent() throws IOException {
        String ddl = read("../sql/mysql-schema.sql");
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS anomaly_event"), "MySQL 应含异常事件表");
        assertTrue(ddl.contains("UNIQUE KEY uk_anomaly_event_id (event_id)"), "MySQL 应含事件唯一键");
        assertTrue(ddl.contains("工单 0412 AX8"), "MySQL DDL 应带工单口径注释");
    }

    @Test
    void postgresqlSchemaDefinesAnomalyEvent() throws IOException {
        String ddl = read("../sql/postgresql-schema.sql");
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS anomaly_event"), "PG 应含异常事件表");
        assertTrue(ddl.contains("CONSTRAINT uk_anomaly_event_id UNIQUE (event_id)"), "PG 应含事件唯一键");
        assertTrue(ddl.contains("COMMENT ON TABLE anomaly_event IS"), "PG 应含表注释");
    }

    private String read(String path) throws IOException {
        File file = new File(path);
        assertTrue(file.isFile(), "schema 文件应存在: " + path);
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
