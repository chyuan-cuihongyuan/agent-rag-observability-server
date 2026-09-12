package cn.chyuan.ai.observability.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * AUTOLOOP al-27 / 工单 1027：application.yml 严格解析契约。
 *
 * G60 清理后的回归守卫：真实 application.yml（本地部署文件，不入库，CI 跳过）
 * 必须能通过 snakeyaml SafeConstructor（无重复顶层键）。
 */
class ApplicationYamlStrictContractTest {

    @Test
    void baseYamlHasNoDuplicateTopLevelKeys() throws Exception {
        assumeTrue(new ClassPathResource("application.yml").exists(), "真实配置不入库，CI 跳过");
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));
        assertThat(sources).isNotEmpty();
        // YamlPropertySourceLoader 底层即严格 snakeyaml：重复键会抛异常使本用例失败
        Object lifecycle = flatGet(sources, "spring.lifecycle.timeout-per-shutdown-phase");
        Object datasourceUrl = flatGet(sources, "spring.datasource.url");
        assertThat(lifecycle).as("合并后两个 spring 块的键都应可解析").isEqualTo("30s");
        assertThat(datasourceUrl).asString().startsWith("jdbc:mysql://");
    }

    private static Object flatGet(java.util.List<PropertySource<?>> sources, String key) {
        for (PropertySource<?> s : sources) {
            Object v = s.getProperty(key);
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
