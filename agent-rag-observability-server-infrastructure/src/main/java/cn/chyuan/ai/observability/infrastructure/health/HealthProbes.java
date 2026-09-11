package cn.chyuan.ai.observability.infrastructure.health;

import cn.chyuan.ai.observability.domain.health.HealthProbe;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 深度健康探测装配（工单 0181 Y5）— DB（SELECT 1，关键）/ ES（ping，非关键）/ MQ（生产模板装配探测，非关键）。
 * 实现内自计耗时；异常归 DOWN 不抛出；向量引擎探针按部署形态在聚合服务侧扩展。
 */
@Slf4j
@Configuration
public class HealthProbes {

    @Bean
    public HealthProbe dbHealthProbe(DataSource dataSource) {
        return new HealthProbe() {
            @Override
            public String name() {
                return "db";
            }

            @Override
            public boolean critical() {
                return true;
            }

            @Override
            public ProbeResult probe() {
                long start = System.currentTimeMillis();
                try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
                    st.execute("SELECT 1");
                    return ProbeResult.ok("db", System.currentTimeMillis() - start);
                } catch (Exception e) {
                    return ProbeResult.fail("db", System.currentTimeMillis() - start, e.getMessage());
                }
            }
        };
    }

    @Bean
    public HealthProbe esHealthProbe(ObjectProvider<ElasticsearchClient> esClientProvider) {
        return new HealthProbe() {
            @Override
            public String name() {
                return "es";
            }

            @Override
            public boolean critical() {
                return false;
            }

            @Override
            public ProbeResult probe() {
                long start = System.currentTimeMillis();
                ElasticsearchClient client = esClientProvider.getIfAvailable();
                if (client == null) {
                    return ProbeResult.fail("es", 0, "ES 客户端未装配");
                }
                try {
                    boolean ping = client.ping().value();
                    return ping ? ProbeResult.ok("es", System.currentTimeMillis() - start)
                            : ProbeResult.fail("es", System.currentTimeMillis() - start, "ping false");
                } catch (Exception e) {
                    return ProbeResult.fail("es", System.currentTimeMillis() - start, e.getMessage());
                }
            }
        };
    }

    @Bean
    public HealthProbe mqHealthProbe(ObjectProvider<org.apache.rocketmq.spring.core.RocketMQTemplate> templateProvider) {
        return new HealthProbe() {
            @Override
            public String name() {
                return "mq";
            }

            @Override
            public boolean critical() {
                return false;
            }

            @Override
            public ProbeResult probe() {
                long start = System.currentTimeMillis();
                var template = templateProvider.getIfAvailable();
                if (template == null) {
                    return ProbeResult.fail("mq", 0, "MQ 未启用（observability.mq.consumer.enabled=false）");
                }
                return ProbeResult.ok("mq", System.currentTimeMillis() - start);
            }
        };
    }
}
