package cn.chyuan.ai.observability.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "observability")
public class ObservabilityClientConfig {

    private MqConfig mq = new MqConfig();
    private HttpConfig http = new HttpConfig();

    @Data
    public static class MqConfig {
        private String topic = "observability-trace";
        private boolean enabled = true;
    }

    @Data
    public static class HttpConfig {
        private String url = "http://localhost:8092";
        private String authKey = "";
        private boolean enabled = true;
        private int timeoutMs = 100;
    }
}
