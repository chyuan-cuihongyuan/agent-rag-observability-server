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
    private OtelConfig otel = new OtelConfig();

    @Data
    public static class MqConfig {
        private String topic = "observability-trace";
        private boolean enabled = true;
    }

    @Data
    public static class HttpConfig {
        private String url = "http://49.232.169.33:8092";
        private String authKey = "";
        private boolean enabled = true;
        private int timeoutMs = 100;
    }

    @Data
    public static class OtelConfig {
        private boolean enabled = true;
    }
}
