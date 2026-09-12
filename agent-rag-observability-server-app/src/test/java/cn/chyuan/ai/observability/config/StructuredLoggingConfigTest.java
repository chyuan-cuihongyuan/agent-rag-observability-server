package cn.chyuan.ai.observability.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * AUTOLOOP al-18 / 工单 1018：结构化日志配置契约。
 * prod 启用 Boot 原生 logstash 结构化格式；dev 保持人类可读 pattern。
 *
 * <p>注：本仓真实 application-{dev,prod}.yml 为本地部署文件不入库（仅 .example 入库），
 * CI 环境无这些文件时用例跳过（assumeTrue），本地部署机上全量校验。
 */
class StructuredLoggingConfigTest {

    private List<PropertySource<?>> load(String resource) throws IOException {
        return new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
    }

    private static boolean resourceExists(String name) {
        return new ClassPathResource(name).exists();
    }

    @Test
    void prodEnablesLogstashStructuredFormat() throws IOException {
        assumeTrue(resourceExists("application-prod.yml"), "真实 prod 配置不入库，CI 跳过");
        var sources = load("application-prod.yml");
        String console = flatGet(sources, "logging.structured.format.console");
        String file = flatGet(sources, "logging.structured.format.file");
        assertThat(console).isEqualTo("logstash");
        assertThat(file).isEqualTo("logstash");
    }

    @Test
    void devKeepsHumanReadablePatternOnly() throws IOException {
        assumeTrue(resourceExists("application-dev.yml"), "真实 dev 配置不入库，CI 跳过");
        // dev 覆盖层不引入 structured（保持人类可读 pattern 语义）
        // 注：base application.yml 存在重复 spring: 键，严格 snakeyaml 无法加载
        // （Spring 自身宽松解析掩盖，已登记 G60 清理主题），故此处只校验 dev 覆盖层
        assertThat(flatGet(load("application-dev.yml"), "logging.structured.format.console")).isNull();
    }

    private static String flatGet(List<PropertySource<?>> sources, String key) {
        for (PropertySource<?> s : sources) {
            Object v = s.getProperty(key);
            if (v != null) {
                return String.valueOf(v);
            }
        }
        return null;
    }
}
