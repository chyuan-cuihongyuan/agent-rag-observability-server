package cn.chyuan.ai.observability.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;

/**
 * AUTOLOOP al-14 / 工单 1014：指标公共维度（借鉴 micrometer-metrics 官方约定）。
 *
 * <p>所有 registry（prometheus/simple）统一注入 common tags：
 * application（服务名）/ env（激活 profile）/ host（主机名）。
 * 业务埋点只关心业务 tag，公共维度在 registry 层一次注入。
 */
@Configuration
public class MetricsCommonTagsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer(
            @Value("${spring.application.name:agent-rag-observability-server}") String application,
            @Value("${spring.profiles.active:default}") String env) {
        return registry -> applyCommonTags(registry, application, env, resolveHost());
    }

    /** 静态注入逻辑，供单测直接验证（不经 Spring 容器）。 */
    static void applyCommonTags(MeterRegistry registry, String application, String env, String host) {
        registry.config().commonTags(
                "application", application,
                "env", env,
                "host", host);
    }

    /** 主机名解析失败回退 "unknown"，不拖累应用启动。 */
    static String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
