package cn.chyuan.ai.observability.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运维契约模板守卫（D07）：application.yml.example 是部署侧（k8s/compose 探针、停机语义）
 * 的参照模板，本测试锁死探针与优雅停机关键键，防模板漂移导致部署侧误配。
 */
class OpsTemplateContractTest {

    @Test
    void templateDeclaresProbesAndGracefulShutdown() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml.example"));
        Properties props = yaml.getObject();

        // 存活/就绪探针
        assertThat(props).isNotNull();
        assertThat(props.getProperty("management.endpoint.health.probes.enabled")).isEqualTo("true");
        assertThat(props.getProperty("management.endpoints.web.exposure.include")).contains("health");
        // 优雅停机
        assertThat(props.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(props.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("30s");
        // D08 日志契约不回退
        assertThat(props.getProperty("logging.pattern.console")).contains("%X{traceId");
    }
}
