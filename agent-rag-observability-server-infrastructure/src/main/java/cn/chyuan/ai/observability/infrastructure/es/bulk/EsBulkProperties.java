package cn.chyuan.ai.observability.infrastructure.es.bulk;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "observability.es.bulk")
public class EsBulkProperties {

    private boolean enabled = false;

    private int maxSize = 200;

    private long flushIntervalMs = 1000;

    private int retryTimes = 3;

    private int queueCapacity = 10000;
}
