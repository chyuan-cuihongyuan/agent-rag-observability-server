package cn.chyuan.ai.observability.config;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示优化实验表 DDL 双方言守卫（工单 0329 AO7，第 32 表）：
 * prompt_optimization_experiment 在 MySQL / PostgreSQL 两份 schema 中均有建表定义。
 */
class PromptOptimDdlTest {

    @Test
    void mysqlSchemaDefinesOptimExperimentTable() throws IOException {
        String ddl = read("../sql/mysql-schema.sql");
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS prompt_optimization_experiment"), "MySQL DDL 应含实验表");
        assertTrue(ddl.contains("signature_fingerprint"), "MySQL 应含签名指纹列");
        assertTrue(ddl.contains("score_curve_json"), "MySQL 应含得分曲线列");
        assertTrue(ddl.contains("'RUNNING'"), "MySQL 应含默认状态");
        assertTrue(ddl.contains("工单 0329 AO7"), "MySQL DDL 应带工单口径注释");
    }

    @Test
    void postgresqlSchemaDefinesOptimExperimentTable() throws IOException {
        String ddl = read("../sql/postgresql-schema.sql");
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS prompt_optimization_experiment"), "PG DDL 应含实验表");
        assertTrue(ddl.contains("signature_fingerprint"), "PG 应含签名指纹列");
        assertTrue(ddl.contains("score_curve_json"), "PG 应含得分曲线列");
        assertTrue(ddl.contains("COMMENT ON TABLE prompt_optimization_experiment IS"), "PG 应含表注释");
        assertTrue(ddl.contains("idx_optim_experiment_created"), "PG 应含创建时间索引");
    }

    private String read(String path) throws IOException {
        File file = new File(path);
        assertTrue(file.isFile(), "schema 文件应存在: " + path);
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
