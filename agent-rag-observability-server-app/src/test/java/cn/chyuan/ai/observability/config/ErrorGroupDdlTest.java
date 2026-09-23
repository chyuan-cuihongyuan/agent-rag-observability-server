package cn.chyuan.ai.observability.config;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误组表 DDL 双方言守卫（工单 0701 CE7，第 36 表）。
 */
class ErrorGroupDdlTest {

    @Test
    void mysqlSchemaDefinesErrorGroup() throws IOException {
        String ddl = read("../sql/mysql-schema.sql");
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS error_group"), "MySQL 应含错误组表");
        assertTrue(ddl.contains("UNIQUE KEY uk_error_group_key (group_key)"), "MySQL 应含组键唯一键");
        assertTrue(ddl.contains("工单 0701 CE7"), "MySQL DDL 应带工单口径注释");
    }

    @Test
    void postgresqlSchemaDefinesErrorGroup() throws IOException {
        String ddl = read("../sql/postgresql-schema.sql");
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS error_group"), "PG 应含错误组表");
        assertTrue(ddl.contains("uk_error_group_key ON error_group"), "PG 应含组键唯一索引");
        assertTrue(ddl.contains("COMMENT ON TABLE error_group IS"), "PG 应含表注释");
    }

    private String read(String path) throws IOException {
        File file = new File(path);
        assertTrue(file.isFile(), "schema 文件应存在: " + path);
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
